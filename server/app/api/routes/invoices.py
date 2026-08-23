"""
Business-level read resource for Invoices (Phase 7A) - GET only. Status is computed inline via
the same `derive_status` port of the Android `InvoiceStatusEngine` the domain layer uses, never
trusted from a stored client-set field. Outstanding-amount computation is imported from
`application/queries/reports.py`'s shared `compute_outstanding_paise` (Phase 7C) - previously a
local duplicate here (added in 7A specifically to avoid importing from `voucher_commands.py`);
now that `reports.py`'s own `outstanding_report` needs the identical computation, one shared
read-query function replaces what would otherwise be two copies. This still never imports from
`voucher_commands.py` - the frozen Phase 6A posting path remains completely untouched.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.application.queries.reports import compute_outstanding_paise
from app.domain.invoice.status import derive_status
from app.infrastructure.database.base import get_db
from app.infrastructure.database.models import Invoice, Voucher

router = APIRouter(prefix="/invoices", tags=["invoices"])


async def _invoice_dict(db: AsyncSession, company_id: str, inv: Invoice) -> dict:
    outstanding = 0
    total_amount_paise = 0
    is_cancelled = False
    if inv.voucher_id:
        result = await db.execute(select(Voucher).where(Voucher.company_id == company_id, Voucher.voucher_id == inv.voucher_id))
        voucher = result.scalar_one_or_none()
        if voucher is not None:
            total_amount_paise = voucher.total_amount_paise
            is_cancelled = voucher.is_cancelled
            outstanding = await compute_outstanding_paise(db, inv.voucher_id, total_amount_paise)

    status = derive_status(
        voucher_id=inv.voucher_id,
        is_cancelled=is_cancelled,
        total_amount_paise=total_amount_paise,
        outstanding_paise=outstanding,
        due_date=inv.due_date,
    )
    return {
        "invoiceId": inv.invoice_id,
        "invoiceType": inv.invoice_type,
        "invoiceNumber": inv.invoice_number,
        "partyId": inv.party_id,
        "date": inv.date,
        "dueDate": inv.due_date,
        "voucherId": inv.voucher_id,
        "referenceInvoiceId": inv.reference_invoice_id,
        "sourceTradeDocumentId": inv.source_trade_document_id,
        "totalAmountPaise": total_amount_paise,
        "outstandingAmountPaise": outstanding,
        "status": status,
    }


@router.get("")
async def list_invoices(
    companyId: str,
    partyId: str | None = None,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    query = select(Invoice).where(Invoice.company_id == companyId)
    if partyId is not None:
        query = query.where(Invoice.party_id == partyId)
    result = await db.execute(query.order_by(Invoice.date.desc()))
    return [await _invoice_dict(db, companyId, inv) for inv in result.scalars().all()]

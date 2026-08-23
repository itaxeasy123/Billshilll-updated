"""
Business-level read resources (Phase 6, Priority 6.13/6.17) - `/sales-invoices`, `/purchase-bills`,
`/receipts`, `/payments`, `/credit-notes`, `/debit-notes`, `/contra`, `/journals`, each a thin GET
filter over the same `vouchers` table by `voucher_type`, not a separate table per endpoint (exactly
as 6.13 specifies). These are READ-only on this server: this app's own mutation path is exclusively
the Outbox batch endpoint (sync.py) - per 6.2/6.3 ("Never: UI -> API -> wait -> accounting entry"),
there is no direct "create a Sales Invoice" HTTP call anywhere in the Android client, so no POST
variant of these routes is built speculatively for a caller that doesn't exist yet.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.infrastructure.database.base import get_db
from app.infrastructure.database.models import Voucher

router = APIRouter(tags=["vouchers"])

_RESOURCE_TO_VOUCHER_TYPE = {
    "sales-invoices": "SALES",
    "purchase-bills": "PURCHASE",
    "receipts": "RECEIPT",
    "payments": "PAYMENT",
    "credit-notes": "CREDIT_NOTE",
    "debit-notes": "DEBIT_NOTE",
    "contra": "CONTRA",
    "journals": "JOURNAL",
}


def _voucher_dict(v: Voucher) -> dict:
    return {
        "voucherId": v.voucher_id,
        "voucherNumber": v.voucher_number,
        "voucherType": v.voucher_type,
        "date": v.date,
        "totalAmountPaise": v.total_amount_paise,
        "isCancelled": v.is_cancelled,
        "referenceVoucherId": v.reference_voucher_id,
    }


async def _list_by_type(voucher_type: str, company_id: str, financial_year_id: str, user_id: str, db: AsyncSession):
    await require_company_access(db, user_id, company_id)
    result = await db.execute(
        select(Voucher).where(
            Voucher.company_id == company_id,
            Voucher.financial_year_id == financial_year_id,
            Voucher.voucher_type == voucher_type,
        ).order_by(Voucher.date.desc())
    )
    return [_voucher_dict(v) for v in result.scalars().all()]


for _resource, _voucher_type in _RESOURCE_TO_VOUCHER_TYPE.items():

    def _make_handler(vt: str):
        async def _handler(
            companyId: str,
            financialYearId: str,
            user_id: str = Depends(get_current_user_id),
            db: AsyncSession = Depends(get_db),
        ):
            return await _list_by_type(vt, companyId, financialYearId, user_id, db)

        return _handler

    router.add_api_route(f"/{_resource}", _make_handler(_voucher_type), methods=["GET"])

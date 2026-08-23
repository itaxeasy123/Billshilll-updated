"""
CREATE_DRAFT_INVOICE / CANCEL_DRAFT_INVOICE / LINK_INVOICE_VOUCHER command handlers (Phase 7A) -
mirrors Android's `AccountingRepository.createDraftInvoice`/`cancelInvoice`/`postInvoice`. A draft
Invoice has zero accounting effect (no Voucher/JournalItem row at all); LINK_INVOICE_VOUCHER only
ever sets `voucher_id` on an already-existing Invoice row. This handler never touches
`apply_voucher_event` or the `vouchers` table itself - the frozen posting path (Phase 6A) is
completely untouched by Phase 7A.

Phase 7B retrofit: `invoice_number` is assigned once, in the CREATE_DRAFT_INVOICE branch below
(by Android's `generateNextDocumentNumber`, carried in the event payload), and is never touched
again by LINK_INVOICE_VOUCHER - it is deliberately NOT assumed to equal the linked Voucher's own
`voucher_number`. `source_trade_document_id` (Phase 7B) records which TradeDocument, if any, this
Invoice was converted from.
"""
from __future__ import annotations

import time

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import InvoiceAlreadyPosted, InvoiceNotFound, ValidationError
from app.infrastructure.database.models import Invoice, InvoiceLine
from app.schemas.sync import SyncEvent


def _now_ms() -> int:
    return int(time.time() * 1000)


async def apply_invoice_event(db: AsyncSession, event: SyncEvent) -> dict:
    if event.invoice is None:
        raise ValidationError("An invoice event must include invoice data.")

    if event.operation == "CREATE_DRAFT_INVOICE":
        now = _now_ms()
        db.add(
            Invoice(
                invoice_id=event.invoice.invoiceId,
                company_id=event.companyId,
                financial_year_id=event.financialYearId,
                invoice_type=event.invoice.invoiceType,
                invoice_number=event.invoice.invoiceNumber,
                party_id=event.invoice.partyId,
                date=event.invoice.date,
                due_date=event.invoice.dueDate,
                voucher_id=None,
                reference_invoice_id=event.invoice.referenceInvoiceId,
                source_trade_document_id=event.invoice.sourceTradeDocumentId,
                narration=event.invoice.narration,
                created_at=now,
                updated_at=now,
            )
        )
        for line in event.invoice.lines:
            db.add(
                InvoiceLine(
                    line_id=line.lineId,
                    invoice_id=event.invoice.invoiceId,
                    item_id=line.itemId,
                    item_name=line.itemName,
                    hsn_sac_code=line.hsnSacCode,
                    quantity_raw=line.quantityRaw,
                    rate_paise=line.ratePaise,
                    gst_rate_percent=line.gstRatePercent,
                    cess_rate_percent=line.cessRatePercent,
                    line_order=line.lineOrder,
                )
            )
        return {"invoiceId": event.invoice.invoiceId, "status": "DRAFT"}

    result = await db.execute(
        select(Invoice).where(Invoice.company_id == event.companyId, Invoice.invoice_id == event.invoice.invoiceId)
    )
    existing = result.scalar_one_or_none()
    if existing is None:
        raise InvoiceNotFound(f"Invoice '{event.invoice.invoiceId}' not found.")

    if event.operation == "CANCEL_DRAFT_INVOICE":
        if existing.voucher_id is not None:
            raise InvoiceAlreadyPosted(
                f"Invoice '{existing.invoice_id}' has already been posted as voucher '{existing.voucher_id}' and cannot be deleted as a draft."
            )
        await db.delete(existing)
        return {"invoiceId": event.invoice.invoiceId, "status": "DELETED"}

    # LINK_INVOICE_VOUCHER - Phase 7B: only ever sets voucher_id, never invoice_number (already
    # assigned at draft-creation time above).
    if existing.voucher_id is not None:
        raise InvoiceAlreadyPosted(f"Invoice '{existing.invoice_id}' is already linked to voucher '{existing.voucher_id}'.")
    existing.voucher_id = event.invoice.voucherId
    existing.updated_at = _now_ms()
    return {"invoiceId": event.invoice.invoiceId, "status": "LINKED"}

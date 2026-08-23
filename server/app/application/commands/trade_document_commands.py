"""
CREATE_TRADE_DOCUMENT / ISSUE_TRADE_DOCUMENT / CONVERT_TRADE_DOCUMENT / CANCEL_TRADE_DOCUMENT
command handlers (Phase 7B) - mirrors Android's AccountingRepository.createTradeDocument/
issueTradeDocument/convertTradeDocument/convertTradeDocumentToInvoice/cancelTradeDocument. A
TradeDocument never creates any Ledger/JournalItem/Voucher row merely by existing.

CONVERT_TRADE_DOCUMENT only ever flips this document's own status to CONVERTED - it never creates
the resulting document/invoice itself (that always arrives as its own, separate
CREATE_TRADE_DOCUMENT or CREATE_DRAFT_INVOICE event, exactly mirroring the two independent outbox
events Android's `convertTradeDocument`/`convertTradeDocumentToInvoice` enqueue). This handler
never touches `apply_voucher_event`/`invoice_commands.py`'s posting-related logic.
"""
from __future__ import annotations

import time

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import TradeDocumentAlreadyConverted, TradeDocumentNotFound, ValidationError
from app.infrastructure.database.models import TradeDocument, TradeDocumentLine
from app.schemas.sync import SyncEvent

_POSTING_DOCUMENT_TYPES = {"SALES_INVOICE", "PURCHASE_BILL", "CREDIT_NOTE", "DEBIT_NOTE"}


def _now_ms() -> int:
    return int(time.time() * 1000)


async def apply_trade_document_event(db: AsyncSession, event: SyncEvent) -> dict:
    if event.tradeDocument is None:
        raise ValidationError("A trade document event must include tradeDocument data.")

    if event.operation == "CREATE_TRADE_DOCUMENT":
        if event.tradeDocument.documentType in _POSTING_DOCUMENT_TYPES:
            raise ValidationError(
                f"{event.tradeDocument.documentType} is a posting document type - create it as an Invoice, not a TradeDocument."
            )
        now = _now_ms()
        db.add(
            TradeDocument(
                trade_document_id=event.tradeDocument.tradeDocumentId,
                company_id=event.companyId,
                financial_year_id=event.financialYearId,
                document_type=event.tradeDocument.documentType,
                document_number=event.tradeDocument.documentNumber,
                party_id=event.tradeDocument.partyId,
                date=event.tradeDocument.date,
                status="DRAFT",
                source_trade_document_id=event.tradeDocument.sourceTradeDocumentId,
                narration=event.tradeDocument.narration,
                created_at=now,
                updated_at=now,
            )
        )
        for line in event.tradeDocument.lines:
            db.add(
                TradeDocumentLine(
                    line_id=line.lineId,
                    trade_document_id=event.tradeDocument.tradeDocumentId,
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
        return {
            "tradeDocumentId": event.tradeDocument.tradeDocumentId,
            "documentNumber": event.tradeDocument.documentNumber,
            "status": "DRAFT",
        }

    result = await db.execute(
        select(TradeDocument).where(
            TradeDocument.company_id == event.companyId,
            TradeDocument.trade_document_id == event.tradeDocument.tradeDocumentId,
        )
    )
    existing = result.scalar_one_or_none()
    if existing is None:
        raise TradeDocumentNotFound(f"Document '{event.tradeDocument.tradeDocumentId}' not found.")

    if event.operation == "ISSUE_TRADE_DOCUMENT":
        if existing.status != "DRAFT":
            raise ValidationError(f"Document '{existing.trade_document_id}' is not a draft (status: {existing.status}).")
        existing.status = "ISSUED"
        existing.updated_at = _now_ms()
        return {"tradeDocumentId": event.tradeDocument.tradeDocumentId, "status": "ISSUED"}

    if event.operation == "CONVERT_TRADE_DOCUMENT":
        if existing.status == "CONVERTED":
            raise TradeDocumentAlreadyConverted(f"Document '{existing.trade_document_id}' has already been converted.")
        if existing.status == "CANCELLED":
            raise ValidationError(f"Document '{existing.trade_document_id}' is cancelled and cannot be converted.")
        existing.status = "CONVERTED"
        existing.updated_at = _now_ms()
        return {"tradeDocumentId": event.tradeDocument.tradeDocumentId, "status": "CONVERTED"}

    # CANCEL_TRADE_DOCUMENT
    if existing.status == "CONVERTED":
        raise ValidationError(f"Document '{existing.trade_document_id}' has already been converted and cannot be cancelled directly.")
    if existing.status == "CANCELLED":
        raise ValidationError(f"Document '{existing.trade_document_id}' is already cancelled.")

    if existing.status == "DRAFT":
        await db.delete(existing)
        return {"tradeDocumentId": event.tradeDocument.tradeDocumentId, "status": "DELETED"}

    existing.status = "CANCELLED"
    existing.updated_at = _now_ms()
    return {"tradeDocumentId": event.tradeDocument.tradeDocumentId, "status": "CANCELLED"}

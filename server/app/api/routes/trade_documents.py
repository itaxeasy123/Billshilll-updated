"""
Business-level routes for TradeDocuments (Phase 7B): a GET list (mirrors `parties.py`'s pattern)
plus one POST route per non-posting document type (Quotation/Proforma Invoice/Sales-Purchase
Order/Delivery-Receipt Note). Android's own client continues to exclusively use the Outbox batch
endpoint for every mutation (offline-first guarantee unchanged) - these POST routes exist for
hypothetical non-Android callers, matching Phase 6A's original design intent (business-level
routes calling the *same* command handlers Outbox dispatch uses, never a raw database mutation).
Each POST route requires an `Idempotency-Key` header and calls `apply_trade_document_event`
directly - the exact same function the Outbox path calls for CREATE_TRADE_DOCUMENT.

Invoice/Sales-Invoice/Purchase-Bill/Credit-Debit-Note stay GET-only (unchanged from 7A) - posting
documents remain exclusively Outbox-driven, since posting them always requires the Android-side
TradingWorkflowEngine/postVoucher path this server never re-implements.
"""
from __future__ import annotations

import json
import time
import uuid

from fastapi import APIRouter, Depends, Header
from pydantic import BaseModel
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.tenant import require_company_access
from app.application.commands.trade_document_commands import apply_trade_document_event
from app.application.services.idempotency import get_stored_response, store_response
from app.domain.errors import AppError
from app.infrastructure.database.base import get_db
from app.infrastructure.database.models import FinancialYear, TradeDocument
from app.schemas.sync import SyncEvent, SyncTradeDocumentDto, SyncTradeDocumentLineDto

router = APIRouter(tags=["trade-documents"])

_RESOURCE_TO_DOCUMENT_TYPE = {
    "quotations": "QUOTATION",
    "proforma-invoices": "PROFORMA_INVOICE",
    "sales-orders": "SALES_ORDER",
    "purchase-orders": "PURCHASE_ORDER",
    "delivery-notes": "DELIVERY_NOTE",
    "receipt-notes": "RECEIPT_NOTE",
}

# Reuses VoucherType's existing prefixes verbatim (Phase 5) - see docs/36_DOCUMENT_LIFECYCLE.md.
_DOCUMENT_PREFIXES = {
    "QUOTATION": "EST-",
    "PROFORMA_INVOICE": "PI-",
    "SALES_ORDER": "SO-",
    "PURCHASE_ORDER": "PO-",
    "DELIVERY_NOTE": "DC-",
    "RECEIPT_NOTE": "GRN-",
}


def _trade_document_dict(d: TradeDocument) -> dict:
    return {
        "tradeDocumentId": d.trade_document_id,
        "documentType": d.document_type,
        "documentNumber": d.document_number,
        "partyId": d.party_id,
        "date": d.date,
        "status": d.status,
        "sourceTradeDocumentId": d.source_trade_document_id,
    }


@router.get("/trade-documents")
async def list_trade_documents(
    companyId: str,
    documentType: str | None = None,
    user_id: str = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    await require_company_access(db, user_id, companyId)
    query = select(TradeDocument).where(TradeDocument.company_id == companyId)
    if documentType is not None:
        query = query.where(TradeDocument.document_type == documentType)
    result = await db.execute(query.order_by(TradeDocument.date.desc()))
    return [_trade_document_dict(d) for d in result.scalars().all()]


class CreateTradeDocumentRequest(BaseModel):
    financialYearId: str
    partyId: str
    date: str
    narration: str = ""
    lines: list[SyncTradeDocumentLineDto]


async def _generate_next_document_number(db: AsyncSession, company_id: str, fy_id: str, document_type: str) -> str:
    """Server-side counterpart to Android's `generateNextDocumentNumber` - only needed here
    because a raw POST caller (unlike Android's Outbox path) never assigns a number client-side."""
    count_result = await db.execute(
        select(func.count()).select_from(TradeDocument).where(
            TradeDocument.company_id == company_id,
            TradeDocument.financial_year_id == fy_id,
            TradeDocument.document_type == document_type,
        )
    )
    count = count_result.scalar_one()
    fy_result = await db.execute(select(FinancialYear).where(FinancialYear.financial_year_id == fy_id))
    fy = fy_result.scalar_one_or_none()
    year_part = fy.start_date[:4] if fy else "2026"
    return f"{_DOCUMENT_PREFIXES[document_type]}{year_part}-{count + 1:04d}"


async def _create_trade_document(
    document_type: str,
    companyId: str,
    body: CreateTradeDocumentRequest,
    idempotency_key: str,
    user_id: str,
    db: AsyncSession,
) -> dict:
    await require_company_access(db, user_id, companyId)
    await db.commit()

    stored = await get_stored_response(db, companyId, idempotency_key)
    if stored is not None:
        return json.loads(stored)

    trade_document_id = uuid.uuid4().hex
    document_number = await _generate_next_document_number(db, companyId, body.financialYearId, document_type)

    event = SyncEvent(
        eventId=uuid.uuid4().hex,
        idempotencyKey=idempotency_key,
        companyId=companyId,
        financialYearId=body.financialYearId,
        operation="CREATE_TRADE_DOCUMENT",
        aggregateType="TRADE_DOCUMENT",
        aggregateId=trade_document_id,
        tradeDocument=SyncTradeDocumentDto(
            tradeDocumentId=trade_document_id,
            documentType=document_type,
            documentNumber=document_number,
            partyId=body.partyId,
            date=body.date,
            status="DRAFT",
            narration=body.narration,
            lines=body.lines,
        ),
    )

    try:
        result = await apply_trade_document_event(db, event)
    except AppError:
        await db.rollback()
        raise

    response_json = json.dumps(result)
    await store_response(db, companyId, idempotency_key, response_json)
    await db.commit()
    return result


for _resource, _document_type in _RESOURCE_TO_DOCUMENT_TYPE.items():

    def _make_handler(dt: str):
        async def _handler(
            companyId: str,
            body: CreateTradeDocumentRequest,
            idempotency_key: str = Header(alias="Idempotency-Key"),
            user_id: str = Depends(get_current_user_id),
            db: AsyncSession = Depends(get_db),
        ):
            return await _create_trade_document(dt, companyId, body, idempotency_key, user_id, db)

        return _handler

    router.add_api_route(f"/{_resource}", _make_handler(_document_type), methods=["POST"])

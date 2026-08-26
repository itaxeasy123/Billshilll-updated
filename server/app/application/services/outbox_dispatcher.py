"""
Dispatches one decoded `SyncEvent` to the right application-layer command handler by `operation`
(Phase 6, Priority 6.13 - "these are API contracts, not necessarily one database table per
endpoint... the backend ultimately invokes the appropriate accounting domain operation"). Wraps
every dispatch in the idempotency check (6.5) and a single DB transaction per event, so one bad
event in a batch rolls back cleanly without touching the others already committed.
"""
from __future__ import annotations

import json

from sqlalchemy.ext.asyncio import AsyncSession

from app.application.commands.gst_transaction_commands import apply_gst_transaction_event
from app.application.commands.invoice_commands import apply_invoice_event
from app.application.commands.ledger_commands import apply_ledger_event
from app.application.commands.party_commands import apply_party_event
from app.application.commands.trade_document_commands import apply_trade_document_event
from app.application.commands.voucher_commands import apply_voucher_event
from app.application.services.idempotency import get_stored_response, store_response
from app.domain.errors import AppError
from app.schemas.sync import SyncEvent

_VOUCHER_OPERATIONS = {
    "POST_SALES_INVOICE", "POST_PURCHASE_BILL", "POST_RECEIPT", "POST_PAYMENT",
    "POST_CONTRA", "POST_JOURNAL", "POST_CREDIT_NOTE", "POST_DEBIT_NOTE",
    "POST_VOUCHER", "CANCEL_VOUCHER",
}
_LEDGER_OPERATIONS = {"CREATE_LEDGER", "UPDATE_LEDGER", "DELETE_LEDGER"}
# Phase 7A - Party + Invoice Domain Foundation. Purely additive: the two sets/branches above are
# unchanged, and posting a Sales Invoice/Purchase Bill/Credit/Debit Note still dispatches through
# _VOUCHER_OPERATIONS exactly as before - these only cover the new pre-posting Party/draft-Invoice
# lifecycle.
_PARTY_OPERATIONS = {"CREATE_PARTY", "UPDATE_PARTY"}
_INVOICE_OPERATIONS = {"CREATE_DRAFT_INVOICE", "CANCEL_DRAFT_INVOICE", "LINK_INVOICE_VOUCHER"}
# Phase 7B - Document/Voucher Lifecycle Architecture. Purely additive: every set/branch above is
# unchanged.
_TRADE_DOCUMENT_OPERATIONS = {"CREATE_TRADE_DOCUMENT", "ISSUE_TRADE_DOCUMENT", "CONVERT_TRADE_DOCUMENT", "CANCEL_TRADE_DOCUMENT"}
# GST-Only Sync Path - purely additive: every set/branch above is unchanged. A GST-only
# transaction never dispatches through _VOUCHER_OPERATIONS/apply_voucher_event - it has no
# accounting effect at all, so it gets its own dedicated command module.
_GST_TRANSACTION_OPERATIONS = {"POST_GST_TRANSACTION"}


class DispatchResult:
    def __init__(self, success: bool, response_json: str, error: AppError | None = None):
        self.success = success
        self.response_json = response_json
        self.error = error


async def dispatch_event(db: AsyncSession, event: SyncEvent) -> DispatchResult:
    stored = await get_stored_response(db, event.companyId, event.idempotencyKey)
    if stored is not None:
        return DispatchResult(success=True, response_json=stored)

    try:
        if event.operation in _VOUCHER_OPERATIONS:
            result = await apply_voucher_event(db, event)
        elif event.operation in _LEDGER_OPERATIONS:
            result = await apply_ledger_event(db, event)
        elif event.operation in _PARTY_OPERATIONS:
            result = await apply_party_event(db, event)
        elif event.operation in _INVOICE_OPERATIONS:
            result = await apply_invoice_event(db, event)
        elif event.operation in _TRADE_DOCUMENT_OPERATIONS:
            result = await apply_trade_document_event(db, event)
        elif event.operation in _GST_TRANSACTION_OPERATIONS:
            result = await apply_gst_transaction_event(db, event)
        else:
            from app.domain.errors import ValidationError
            raise ValidationError(f"Unknown sync operation '{event.operation}'.")

        response_json = json.dumps(result)
        await store_response(db, event.companyId, event.idempotencyKey, response_json)
        return DispatchResult(success=True, response_json=response_json)
    except AppError as e:
        return DispatchResult(success=False, response_json=json.dumps({"code": e.code, "message": e.message}), error=e)

"""
Accounting-rule rejection tests (Phase 6, Priority 6.17): unbalanced transaction, locked period,
invalid Contra, over-allocation, duplicate voucher, cancelled-voucher-can't-repost-incorrectly.
Every one of these is the SERVER's own independent check, not a trust of the client.
"""
from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

from tests.helpers import make_batch_request, make_sync_event

pytestmark = pytest.mark.asyncio


async def _post_one(client: AsyncClient, company_id: str, event: dict, headers: dict, idem_key: str | None = None):
    batch = make_batch_request(company_id, [event])
    return await client.post("/sync/outbox/batch", json=batch, headers={**headers, "Idempotency-Key": idem_key or uuid.uuid4().hex})


async def test_unbalanced_voucher_rejected(client: AsyncClient, auth_headers: dict):
    event = make_sync_event("COMP_REJ1")
    # Tamper one line so debit != credit.
    event["journalLines"][1]["amountPaise"] = event["journalLines"][1]["amountPaise"] - 1

    resp = await _post_one(client, "COMP_REJ1", event, auth_headers)
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "DOUBLE_ENTRY_NOT_BALANCED"


async def test_locked_period_rejected(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import AccountingPeriod

    db_session.add(
        AccountingPeriod(
            period_id="PER_LOCKED", company_id="COMP_REJ2", financial_year_id="FY_TEST",
            name="Apr 2026", start_date="2026-04-01", end_date="2026-04-30", status="LOCKED",
        )
    )
    await db_session.commit()

    event = make_sync_event("COMP_REJ2")
    event["voucher"]["date"] = "2026-04-15"

    resp = await _post_one(client, "COMP_REJ2", event, auth_headers)
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "PERIOD_LOCKED"


async def test_invalid_contra_rejected(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import Ledger

    db_session.add_all([
        Ledger(ledger_id="LED_BANK", company_id="COMP_REJ3", group_id="GRP_BANK_COMP_REJ3", name="Bank"),
        Ledger(ledger_id="LED_SALES", company_id="COMP_REJ3", group_id="GRP_SALES_COMP_REJ3", name="Sales"),
    ])
    await db_session.commit()

    event = make_sync_event("COMP_REJ3", operation="POST_CONTRA", debit_ledger="LED_SALES", credit_ledger="LED_BANK")
    resp = await _post_one(client, "COMP_REJ3", event, auth_headers)
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "INVALID_CONTRA"


async def test_valid_contra_between_cash_and_bank_accepted(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import Ledger

    db_session.add_all([
        Ledger(ledger_id="LED_BANK2", company_id="COMP_REJ3B", group_id="GRP_BANK_COMP_REJ3B", name="Bank"),
        Ledger(ledger_id="LED_CASH2", company_id="COMP_REJ3B", group_id="GRP_CASH_COMP_REJ3B", name="Cash"),
    ])
    await db_session.commit()

    event = make_sync_event("COMP_REJ3B", operation="POST_CONTRA", debit_ledger="LED_CASH2", credit_ledger="LED_BANK2")
    resp = await _post_one(client, "COMP_REJ3B", event, auth_headers)
    assert resp.json()["processedCount"] == 1


async def test_over_allocation_rejected(client: AsyncClient, auth_headers: dict):
    invoice_event = make_sync_event("COMP_REJ4", operation="POST_SALES_INVOICE", amount_paise=100_000, voucher_id="V_INV1", voucher_number="INV-0001")
    await _post_one(client, "COMP_REJ4", invoice_event, auth_headers)

    settlement_event = make_sync_event("COMP_REJ4", operation="POST_RECEIPT", amount_paise=100_000, voucher_id="V_RCT1", voucher_number="RCT-0001")
    settlement_event["settlements"] = [
        {"allocationId": uuid.uuid4().hex, "settlementVoucherId": "V_RCT1", "invoiceVoucherId": "V_INV1", "allocatedAmountPaise": 150_000}
    ]
    resp = await _post_one(client, "COMP_REJ4", settlement_event, auth_headers)
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "OVER_ALLOCATION"


async def test_duplicate_voucher_number_rejected(client: AsyncClient, auth_headers: dict):
    event1 = make_sync_event("COMP_REJ5", voucher_number="JRN-DUP-0001")
    event2 = make_sync_event("COMP_REJ5", voucher_number="JRN-DUP-0001")

    resp1 = await _post_one(client, "COMP_REJ5", event1, auth_headers)
    assert resp1.json()["processedCount"] == 1

    resp2 = await _post_one(client, "COMP_REJ5", event2, auth_headers)
    body = resp2.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "DUPLICATE_VOUCHER"


async def test_cancelled_voucher_cannot_be_cancelled_again(client: AsyncClient, auth_headers: dict):
    post_event = make_sync_event("COMP_REJ6", voucher_id="V_TO_CANCEL", voucher_number="JRN-0099")
    await _post_one(client, "COMP_REJ6", post_event, auth_headers)

    cancel_event = make_sync_event("COMP_REJ6", operation="CANCEL_VOUCHER", voucher_id="V_TO_CANCEL", voucher_number="JRN-0099")
    first_cancel = await _post_one(client, "COMP_REJ6", cancel_event, auth_headers)
    assert first_cancel.json()["processedCount"] == 1

    # Same target voucher, a genuinely new cancellation attempt (fresh idempotency key/eventId) -
    # must be rejected because the voucher is already cancelled, not silently re-applied.
    second_cancel_event = make_sync_event("COMP_REJ6", operation="CANCEL_VOUCHER", voucher_id="V_TO_CANCEL", voucher_number="JRN-0099")
    second_cancel = await _post_one(client, "COMP_REJ6", second_cancel_event, auth_headers)
    body = second_cancel.json()
    assert body["processedCount"] == 0
    assert "already cancelled" in body["rejections"][0]["reason"]

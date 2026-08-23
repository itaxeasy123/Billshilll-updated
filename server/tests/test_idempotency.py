"""Idempotency tests (Phase 6, Priority 6.17): first request posts, duplicate request does not
post twice."""
from __future__ import annotations

import pytest
from httpx import AsyncClient

from tests.helpers import make_batch_request, make_sync_event

pytestmark = pytest.mark.asyncio


async def test_duplicate_idempotency_key_does_not_reprocess(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import Ledger

    # trial_balance() reports one row per existing Ledger master row, not per distinct ledgerId
    # seen in journal_items - these two must exist for the assertion below to find anything at all.
    db_session.add_all([
        Ledger(ledger_id="LED_BANK", company_id="COMP_IDEMP", group_id="GRP_BANK_COMP_IDEMP", name="Bank"),
        Ledger(ledger_id="LED_CAPITAL", company_id="COMP_IDEMP", group_id="GRP_CAPITAL_COMP_IDEMP", name="Capital"),
    ])
    await db_session.commit()

    event = make_sync_event("COMP_IDEMP")
    batch = make_batch_request("COMP_IDEMP", [event])

    first = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "b1"})
    assert first.status_code == 200
    assert first.json()["processedCount"] == 1

    # Same idempotencyKey, same event, sent again (simulating a retried/duplicate HTTP request).
    second = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "b2"})
    assert second.status_code == 200
    # It's still reported as "processed" (idempotent success), but must not create a second voucher.
    assert second.json()["processedCount"] == 1

    trial_balance = await client.get(
        "/reports/trial-balance", params={"companyId": "COMP_IDEMP", "financialYearId": "FY_TEST"}, headers=auth_headers
    )
    row = next(r for r in trial_balance.json()["rows"] if r["ledgerId"] == "LED_BANK")
    # If the event had been reprocessed, this would be double the posted amount.
    assert row["debitPaise"] == event["voucher"]["totalAmountPaise"]


async def test_new_idempotency_key_for_same_voucher_number_is_rejected_as_duplicate(client: AsyncClient, auth_headers: dict):
    event1 = make_sync_event("COMP_IDEMP2", voucher_number="JRN-FIXED-0001")
    event2 = make_sync_event("COMP_IDEMP2", voucher_number="JRN-FIXED-0001")  # same number, different voucherId/idempotencyKey

    batch1 = make_batch_request("COMP_IDEMP2", [event1])
    resp1 = await client.post("/sync/outbox/batch", json=batch1, headers={**auth_headers, "Idempotency-Key": "b3"})
    assert resp1.json()["processedCount"] == 1

    batch2 = make_batch_request("COMP_IDEMP2", [event2])
    resp2 = await client.post("/sync/outbox/batch", json=batch2, headers={**auth_headers, "Idempotency-Key": "b4"})
    body = resp2.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "DUPLICATE_VOUCHER"

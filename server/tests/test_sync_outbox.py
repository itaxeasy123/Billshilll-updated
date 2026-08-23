"""
Offline-sync batch tests (Phase 6, Priority 6.17: "offline mutation -> outbox -> reconnect ->
upload -> acknowledgement -> synced"). The server side of that flow is exactly what
`/sync/outbox/batch` does: accept everything that accumulated while offline in one batch, process
each independently, and acknowledge which ones succeeded (`processedSyncIds`) - Android marks
those SYNCED and leaves the rest PENDING/FAILED for retry, exactly mirroring
`OutboxProcessor.processPendingOutbox`.
"""
from __future__ import annotations

import json
import uuid

import pytest
from httpx import AsyncClient

from tests.helpers import make_batch_request, make_sync_event

pytestmark = pytest.mark.asyncio


async def test_batch_of_multiple_offline_mutations_all_acknowledged(client: AsyncClient, auth_headers: dict):
    events = [make_sync_event("COMP_SYNC1", voucher_number=f"JRN-{i:04d}") for i in range(5)]
    batch = make_batch_request("COMP_SYNC1", events)

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["success"] is True
    assert body["processedCount"] == 5
    assert set(body["processedSyncIds"]) == {item["syncId"] for item in batch["items"]}
    assert body["rejections"] == []


async def test_one_bad_item_in_batch_does_not_block_the_rest(client: AsyncClient, auth_headers: dict):
    good_event = make_sync_event("COMP_SYNC2", voucher_number="JRN-GOOD")
    batch = make_batch_request("COMP_SYNC2", [good_event])
    # Corrupt the second item's payload directly (simulating a malformed/older-schema payload that
    # slipped into the outbox) without touching the first, valid one.
    batch["items"].append(
        {
            "syncId": uuid.uuid4().hex, "entityType": "VOUCHER", "entityId": "V_BAD", "operation": "POST_JOURNAL",
            "payloadJson": "{not valid json", "idempotencyKey": uuid.uuid4().hex, "version": 1, "clientTimestamp": 0,
        }
    )

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 1
    assert len(body["rejections"]) == 1
    assert body["rejections"][0]["syncId"] == batch["items"][1]["syncId"]


async def test_acknowledged_items_are_queryable_afterward(client: AsyncClient, auth_headers: dict):
    event = make_sync_event("COMP_SYNC3", voucher_number="JRN-VERIFY")
    batch = make_batch_request("COMP_SYNC3", [event])
    await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})

    resp = await client.get("/journals", params={"companyId": "COMP_SYNC3", "financialYearId": "FY_TEST"}, headers=auth_headers)
    assert resp.status_code == 200
    voucher_numbers = [v["voucherNumber"] for v in resp.json()]
    assert "JRN-VERIFY" in voucher_numbers

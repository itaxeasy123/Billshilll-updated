"""
Security tests (Phase 6, Priority 6.17): unauthorized endpoint, forbidden company, malformed
payload, replay attempt, invalid idempotency key.
"""
from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

from tests.helpers import make_batch_request, make_sync_event

pytestmark = pytest.mark.asyncio


async def test_unauthorized_endpoint_access_rejected(client: AsyncClient):
    """No Authorization header at all on a protected mutation endpoint."""
    batch = make_batch_request("COMP_SEC1", [make_sync_event("COMP_SEC1")])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={"Idempotency-Key": uuid.uuid4().hex})
    assert resp.status_code == 401
    assert resp.json()["code"] == "AUTHENTICATION_REQUIRED"


async def test_forbidden_company_access_rejected(client: AsyncClient):
    """A different, unrelated user cannot read a company they were never granted membership to."""
    owner = await client.post("/auth/register", json={"email": "sec-owner@example.com", "password": "correct horse battery staple"})
    intruder = await client.post("/auth/register", json={"email": "sec-intruder@example.com", "password": "correct horse battery staple"})
    owner_headers = {"Authorization": f"Bearer {owner.json()['accessToken']}"}
    intruder_headers = {"Authorization": f"Bearer {intruder.json()['accessToken']}"}

    claim_batch = make_batch_request("COMP_SEC2", [make_sync_event("COMP_SEC2")])
    await client.post("/sync/outbox/batch", json=claim_batch, headers={**owner_headers, "Idempotency-Key": uuid.uuid4().hex})

    resp = await client.get("/reports/trial-balance", params={"companyId": "COMP_SEC2", "financialYearId": "FY_TEST"}, headers=intruder_headers)
    assert resp.status_code == 403
    assert resp.json()["code"] == "TENANT_MISMATCH"


async def test_malformed_payload_rejected(client: AsyncClient, auth_headers: dict):
    """A request body missing required fields fails validation cleanly (FastAPI/Pydantic 422),
    never a raw 500 or an internal stack trace leaked to the client."""
    resp = await client.post(
        "/sync/outbox/batch",
        json={"companyId": "COMP_SEC3"},  # missing required "deviceId" and "items"
        headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex},
    )
    assert resp.status_code == 422


async def test_replay_of_identical_batch_request_has_no_additional_effect(client: AsyncClient, auth_headers: dict):
    """Replaying the exact same batch (same idempotencyKeys throughout) must not double-post -
    covers the "duplicate HTTP request" scenario from 6.5 at the full-request level, not just a
    single event."""
    event = make_sync_event("COMP_SEC4", voucher_number="JRN-REPLAY")
    batch = make_batch_request("COMP_SEC4", [event])

    first = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "replay-key"})
    second = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "replay-key"})
    assert first.json()["processedCount"] == 1
    assert second.json()["processedCount"] == 1

    listing = await client.get("/journals", params={"companyId": "COMP_SEC4", "financialYearId": "FY_TEST"}, headers=auth_headers)
    matching = [v for v in listing.json() if v["voucherNumber"] == "JRN-REPLAY"]
    assert len(matching) == 1  # not duplicated


async def test_missing_idempotency_key_header_rejected(client: AsyncClient, auth_headers: dict):
    batch = make_batch_request("COMP_SEC5", [make_sync_event("COMP_SEC5")])
    resp = await client.post("/sync/outbox/batch", json=batch, headers=auth_headers)  # no Idempotency-Key header
    assert resp.status_code == 422

"""
Tenant isolation tests (Phase 6, Priority 6.17): Company A cannot read or mutate Company B's data.
Each user auto-provisions OWNER on the first company they ever sync (tenant.py's bootstrap rule);
once a company has an owner, no other user can touch it.
"""
from __future__ import annotations

import pytest
from httpx import AsyncClient

from tests.helpers import make_batch_request, make_sync_event

pytestmark = pytest.mark.asyncio


async def _register_and_login(client: AsyncClient, email: str) -> dict:
    resp = await client.post("/auth/register", json={"email": email, "password": "correct horse battery staple"})
    assert resp.status_code == 200
    return resp.json()


async def test_company_a_cannot_read_company_b(client: AsyncClient):
    user_a = await _register_and_login(client, "usera@example.com")
    user_b = await _register_and_login(client, "userb@example.com")
    headers_a = {"Authorization": f"Bearer {user_a['accessToken']}"}
    headers_b = {"Authorization": f"Bearer {user_b['accessToken']}"}

    # User A claims COMP_A by being the first to sync it (auto-provision).
    batch = make_batch_request("COMP_A", [make_sync_event("COMP_A")])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**headers_a, "Idempotency-Key": "batch-1"})
    assert resp.status_code == 200
    assert resp.json()["processedCount"] == 1

    # User B must not be able to read COMP_A's trial balance.
    resp = await client.get("/reports/trial-balance", params={"companyId": "COMP_A", "financialYearId": "FY_TEST"}, headers=headers_b)
    assert resp.status_code == 403
    assert resp.json()["code"] == "TENANT_MISMATCH"


async def test_company_a_cannot_read_company_b_via_new_report_routes(client: AsyncClient):
    """Phase 7C added 9 new report routes (profit-loss, balance-sheet, ledger, day-book,
    outstanding, receivables, payables, cash-flow, ratios). Each declares the same
    `require_company_access` dependency `trial-balance` already has direct cross-tenant coverage
    for above - this test exercises that guarantee directly at each new route rather than only
    inferring it from shared middleware behavior."""
    user_a = await _register_and_login(client, "usera3@example.com")
    user_b = await _register_and_login(client, "userb3@example.com")
    headers_a = {"Authorization": f"Bearer {user_a['accessToken']}"}
    headers_b = {"Authorization": f"Bearer {user_b['accessToken']}"}

    batch = make_batch_request("COMP_C", [make_sync_event("COMP_C")])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**headers_a, "Idempotency-Key": "batch-c1"})
    assert resp.status_code == 200
    assert resp.json()["processedCount"] == 1

    params = {"companyId": "COMP_C", "financialYearId": "FY_TEST", "ledgerId": "L", "startDate": "2026-01-01", "endDate": "2026-01-31"}
    for path in ["/reports/profit-loss", "/reports/balance-sheet", "/reports/ledger", "/reports/day-book",
                 "/reports/outstanding", "/reports/receivables", "/reports/payables", "/reports/cash-flow", "/reports/ratios"]:
        resp = await client.get(path, params=params, headers=headers_b)
        assert resp.status_code == 403, f"{path} did not reject a cross-tenant read"
        assert resp.json()["code"] == "TENANT_MISMATCH", f"{path} rejected but with the wrong error code"


async def test_company_a_cannot_mutate_company_b(client: AsyncClient):
    user_a = await _register_and_login(client, "usera2@example.com")
    user_b = await _register_and_login(client, "userb2@example.com")
    headers_a = {"Authorization": f"Bearer {user_a['accessToken']}"}
    headers_b = {"Authorization": f"Bearer {user_b['accessToken']}"}

    # User B claims COMP_B first.
    claim_batch = make_batch_request("COMP_B", [make_sync_event("COMP_B")])
    resp = await client.post("/sync/outbox/batch", json=claim_batch, headers={**headers_b, "Idempotency-Key": "claim-1"})
    assert resp.status_code == 200
    assert resp.json()["processedCount"] == 1

    # User A now tries to post a voucher into COMP_B - must be rejected as TENANT_MISMATCH, not silently accepted.
    intrusion_batch = make_batch_request("COMP_B", [make_sync_event("COMP_B")])
    resp = await client.post("/sync/outbox/batch", json=intrusion_batch, headers={**headers_a, "Idempotency-Key": "intrusion-1"})
    assert resp.status_code == 200  # batch endpoint always 200s; rejection is per-item/whole-batch inside the body
    body = resp.json()
    assert body["processedCount"] == 0
    assert all(r["conflictCode"] == "TENANT_MISMATCH" for r in body["rejections"])

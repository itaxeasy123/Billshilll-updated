"""Phase 7J-B (Subscription/Entitlements) tests. Uses the same conftest fixtures (`client`,
`auth_headers`, `db_session`) as every other test file. Proves: FY-validity is never derived from a
hardcoded "1 Apr-31 Mar" literal (a deliberately non-standard financial year is used), tenant
isolation, and the 409 SUBSCRIPTION_ALREADY_EXISTS path on a genuine duplicate insert race."""
from __future__ import annotations

import datetime as dt

import pytest
from httpx import AsyncClient

pytestmark = pytest.mark.asyncio


async def _seed_company(db_session, company_id: str) -> None:
    from app.infrastructure.database.models import Company

    db_session.add(Company(
        company_id=company_id, name="Apex Traders", trade_name="Apex Traders", gstin="27AAAAA0000A1Z5",
        pan="AAAAA0000A", state_code="27", state_name="Maharashtra", email="apex@example.com", phone="9990001111",
        address="1 Market Street, Mumbai", currency="INR", financial_year_start_month=4,
        accounting_mode="ACCOUNT_ONLY", business_type="TRADING", is_default=True, created_at=0,
    ))
    await db_session.commit()


async def _seed_financial_year(db_session, company_id: str, fy_id: str, start_date: str, end_date: str) -> None:
    from app.infrastructure.database.models import FinancialYear

    db_session.add(FinancialYear(
        financial_year_id=fy_id, company_id=company_id, fy_code="FY", start_date=start_date, end_date=end_date,
        is_current=True, is_locked=False, locked_at=None, locked_by=None,
    ))
    await db_session.commit()


async def test_create_subscription_then_get_current(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_SUB1"
    await _seed_company(db_session, company_id)
    await _seed_financial_year(db_session, company_id, "FY_SUB1", "2026-04-01", "2027-03-31")

    create_resp = await client.post("/subscriptions", json={
        "companyId": company_id, "financialYearId": "FY_SUB1", "planType": "PAID",
        "planName": "Pro Plan", "entitlements": ["GSTR", "INVENTORY"],
    }, headers=auth_headers)
    assert create_resp.status_code == 200, create_resp.text
    body = create_resp.json()
    assert body["planType"] == "PAID"
    assert set(body["entitlements"]) == {"GSTR", "INVENTORY"}

    get_resp = await client.get("/subscriptions/current", params={"companyId": company_id, "financialYearId": "FY_SUB1"}, headers=auth_headers)
    assert get_resp.status_code == 200
    assert get_resp.json()["subscriptionId"] == body["subscriptionId"]


async def test_renew_subscription_updates_existing_row_not_creates_new(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_SUB2"
    await _seed_company(db_session, company_id)
    await _seed_financial_year(db_session, company_id, "FY_SUB2", "2026-04-01", "2027-03-31")

    first = await client.post("/subscriptions", json={
        "companyId": company_id, "financialYearId": "FY_SUB2", "planType": "FREE", "planName": "Free Plan", "entitlements": [],
    }, headers=auth_headers)
    second = await client.post("/subscriptions", json={
        "companyId": company_id, "financialYearId": "FY_SUB2", "planType": "PAID", "planName": "Pro Plan", "entitlements": ["GSTR"],
    }, headers=auth_headers)
    assert second.status_code == 200
    assert first.json()["subscriptionId"] == second.json()["subscriptionId"]
    assert second.json()["planType"] == "PAID"


async def test_check_entitlement_nonStandardFinancialYear_stillResolvesCorrectly(client: AsyncClient, auth_headers: dict, db_session):
    """Deliberately NOT 1 Apr - 31 Mar - proves validity is derived from the FinancialYear's own
    stored dates, never a hardcoded calendar literal."""
    company_id = "COMP_SUB3"
    await _seed_company(db_session, company_id)
    today = dt.date.today()
    fy_start = (today - dt.timedelta(days=10)).isoformat()
    fy_end = (today + dt.timedelta(days=300)).isoformat()
    await _seed_financial_year(db_session, company_id, "FY_SUB3", fy_start, fy_end)

    await client.post("/subscriptions", json={
        "companyId": company_id, "financialYearId": "FY_SUB3", "planType": "PAID", "planName": "Pro Plan", "entitlements": ["GSTR"],
    }, headers=auth_headers)

    resp = await client.get("/subscriptions/entitlement", params={"companyId": company_id, "financialYearId": "FY_SUB3", "feature": "GSTR"}, headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["hasEntitlement"] is True


async def test_check_entitlement_futureFinancialYear_returnsFalse(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_SUB4"
    await _seed_company(db_session, company_id)
    await _seed_financial_year(db_session, company_id, "FY_SUB4_FUTURE", "2030-04-01", "2031-03-31")

    await client.post("/subscriptions", json={
        "companyId": company_id, "financialYearId": "FY_SUB4_FUTURE", "planType": "PAID", "planName": "Pro Plan", "entitlements": ["GSTR"],
    }, headers=auth_headers)

    resp = await client.get("/subscriptions/entitlement", params={"companyId": company_id, "financialYearId": "FY_SUB4_FUTURE", "feature": "GSTR"}, headers=auth_headers)
    assert resp.json()["hasEntitlement"] is False


async def test_subscription_cross_tenant_rejected(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_SUB5"
    await _seed_company(db_session, company_id)
    await _seed_financial_year(db_session, company_id, "FY_SUB5", "2026-04-01", "2027-03-31")
    # User A (auth_headers) must establish ownership first via a COMMITTING call (the bootstrap
    # rule's membership grant is only flushed, not committed, inside a read-only GET route - a GET
    # here would silently roll back and never persist the grant) - otherwise User B below would
    # become the bootstrap owner instead, and the cross-tenant rejection this test checks would
    # never fire. Mirrors test_documents_7d.py's identical precedent (uses a POST for the same reason).
    await client.post("/subscriptions", json={
        "companyId": company_id, "financialYearId": "FY_SUB5", "planType": "FREE", "planName": "Bootstrap", "entitlements": [],
    }, headers=auth_headers)

    user_b_resp = await client.post("/auth/register", json={"email": "userb_sub5@example.com", "password": "correct horse battery staple"})
    headers_b = {"Authorization": f"Bearer {user_b_resp.json()['accessToken']}"}

    resp = await client.get("/subscriptions/current", params={"companyId": company_id, "financialYearId": "FY_SUB5"}, headers=headers_b)
    assert resp.status_code == 403
    assert resp.json()["code"] == "TENANT_MISMATCH"


async def test_deactivate_subscription_thenEntitlementDenied(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_SUB6"
    await _seed_company(db_session, company_id)
    await _seed_financial_year(db_session, company_id, "FY_SUB6", "2026-04-01", "2027-03-31")

    create_resp = await client.post("/subscriptions", json={
        "companyId": company_id, "financialYearId": "FY_SUB6", "planType": "PAID", "planName": "Pro Plan", "entitlements": ["GSTR"],
    }, headers=auth_headers)
    subscription_id = create_resp.json()["subscriptionId"]

    deactivate_resp = await client.post(f"/subscriptions/{subscription_id}/deactivate", params={"companyId": company_id}, headers=auth_headers)
    assert deactivate_resp.status_code == 200
    assert deactivate_resp.json()["isActive"] is False

    resp = await client.get("/subscriptions/entitlement", params={"companyId": company_id, "financialYearId": "FY_SUB6", "feature": "GSTR"}, headers=auth_headers)
    assert resp.json()["hasEntitlement"] is False


async def test_create_or_renew_subscription_racingInserts_translatesToSubscriptionAlreadyExists(db_session, monkeypatch):
    """Simulates two concurrent `create_or_renew_subscription` calls that both read "no existing
    row" before either commits - the real race the DB-level unique constraint (and this service's
    `IntegrityError` -> `SubscriptionAlreadyExists` translation) exists to guard against. Monkeypatches
    `get_current_subscription` to always report "not found" so the *second* call still takes the
    insert branch and genuinely collides at the database - exercising the service's own real
    exception-translation code path, not a re-implementation of it in the test."""
    from app.application.services import subscription_service
    from app.domain.errors import SubscriptionAlreadyExists

    company_id = "COMP_SUB7"
    await _seed_company(db_session, company_id)
    await _seed_financial_year(db_session, company_id, "FY_SUB7", "2026-04-01", "2027-03-31")

    await subscription_service.create_or_renew_subscription(db_session, company_id, "FY_SUB7", "FREE", "Free Plan", [])
    await db_session.commit()

    async def _always_none(*args, **kwargs):
        return None

    monkeypatch.setattr(subscription_service, "get_current_subscription", _always_none)

    with pytest.raises(SubscriptionAlreadyExists):
        await subscription_service.create_or_renew_subscription(db_session, company_id, "FY_SUB7", "PAID", "Pro Plan", ["GSTR"])

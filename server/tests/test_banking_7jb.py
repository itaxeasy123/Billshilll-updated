"""Phase 7J-B (Bank/UPI Profile - "Cash/Bank" settlement-metadata scope) tests. Uses the same
conftest fixtures (`client`, `auth_headers`, `db_session`) as every other test file."""
from __future__ import annotations

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


async def test_create_bank_upi_profile_then_list(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_BANK1"
    await _seed_company(db_session, company_id)

    create_resp = await client.post("/bank-upi-profiles", json={
        "companyId": company_id, "bankName": "HDFC Bank", "accountHolderName": "Apex Traders",
        "accountNumber": "1234567890", "ifscCode": "HDFC0000001", "upiId": "apex@hdfc", "upiPayeeName": "Apex Traders",
    }, headers=auth_headers)
    assert create_resp.status_code == 200, create_resp.text
    profile_id = create_resp.json()["bankUpiProfileId"]

    list_resp = await client.get("/bank-upi-profiles", params={"companyId": company_id}, headers=auth_headers)
    assert list_resp.status_code == 200
    profiles = list_resp.json()
    assert len(profiles) == 1
    assert profiles[0]["bankUpiProfileId"] == profile_id
    assert profiles[0]["upiId"] == "apex@hdfc"


async def test_update_bank_upi_profile(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_BANK2"
    await _seed_company(db_session, company_id)

    create_resp = await client.post("/bank-upi-profiles", json={
        "companyId": company_id, "bankName": "HDFC Bank", "accountNumber": "1234567890", "ifscCode": "HDFC0000001",
    }, headers=auth_headers)
    profile_id = create_resp.json()["bankUpiProfileId"]

    update_resp = await client.put(f"/bank-upi-profiles/{profile_id}", json={
        "companyId": company_id, "bankName": "ICICI Bank",
    }, headers=auth_headers)
    assert update_resp.status_code == 200
    assert update_resp.json()["bankName"] == "ICICI Bank"
    assert update_resp.json()["accountNumber"] == "1234567890"  # untouched fields survive a partial update


async def test_delete_bank_upi_profile(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_BANK3"
    await _seed_company(db_session, company_id)

    create_resp = await client.post("/bank-upi-profiles", json={"companyId": company_id, "bankName": "HDFC Bank"}, headers=auth_headers)
    profile_id = create_resp.json()["bankUpiProfileId"]

    delete_resp = await client.delete(f"/bank-upi-profiles/{profile_id}", params={"companyId": company_id}, headers=auth_headers)
    assert delete_resp.status_code == 200
    assert delete_resp.json()["deleted"] is True

    list_resp = await client.get("/bank-upi-profiles", params={"companyId": company_id}, headers=auth_headers)
    assert list_resp.json() == []


async def test_list_bank_upi_profiles_scopedByParty(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_BANK4"
    await _seed_company(db_session, company_id)

    await client.post("/bank-upi-profiles", json={"companyId": company_id, "partyId": "PTY_1", "bankName": "Party Bank"}, headers=auth_headers)
    await client.post("/bank-upi-profiles", json={"companyId": company_id, "bankName": "Company Bank"}, headers=auth_headers)

    scoped_resp = await client.get("/bank-upi-profiles", params={"companyId": company_id, "partyId": "PTY_1"}, headers=auth_headers)
    assert len(scoped_resp.json()) == 1
    assert scoped_resp.json()[0]["partyId"] == "PTY_1"


async def test_bank_upi_profile_cross_tenant_rejected(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_BANK5"
    await _seed_company(db_session, company_id)
    # User A (auth_headers) must establish ownership first via a COMMITTING call (the bootstrap
    # rule's membership grant is only flushed, not committed, inside a read-only GET route - a GET
    # here would silently roll back and never persist the grant) - otherwise User B below would
    # become the bootstrap owner instead, and the cross-tenant rejection this test checks would
    # never fire. Mirrors test_documents_7d.py's identical precedent (uses a POST for the same reason).
    await client.post("/bank-upi-profiles", json={"companyId": company_id, "bankName": "Bootstrap"}, headers=auth_headers)

    user_b_resp = await client.post("/auth/register", json={"email": "userb_bank5@example.com", "password": "correct horse battery staple"})
    headers_b = {"Authorization": f"Bearer {user_b_resp.json()['accessToken']}"}

    resp = await client.get("/bank-upi-profiles", params={"companyId": company_id}, headers=headers_b)
    assert resp.status_code == 403
    assert resp.json()["code"] == "TENANT_MISMATCH"


async def test_get_unknown_bank_upi_profile_returns_validation_error(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_BANK6"
    await _seed_company(db_session, company_id)

    resp = await client.get("/bank-upi-profiles/NO_SUCH_PROFILE", params={"companyId": company_id}, headers=auth_headers)
    assert resp.status_code == 400
    assert resp.json()["code"] == "VALIDATION_ERROR"


async def test_zero_ledger_voucher_effect_from_bank_upi_profile_crud(client: AsyncClient, auth_headers: dict, db_session):
    """Pure metadata guarantee: creating/updating/deleting a Bank/UPI profile never touches the
    ledgers/vouchers/journal_items tables."""
    from sqlalchemy import select

    from app.infrastructure.database.models import JournalItem, Ledger, Voucher

    company_id = "COMP_BANK7"
    await _seed_company(db_session, company_id)

    create_resp = await client.post("/bank-upi-profiles", json={"companyId": company_id, "bankName": "HDFC Bank"}, headers=auth_headers)
    profile_id = create_resp.json()["bankUpiProfileId"]
    await client.put(f"/bank-upi-profiles/{profile_id}", json={"companyId": company_id, "bankName": "ICICI Bank"}, headers=auth_headers)
    await client.delete(f"/bank-upi-profiles/{profile_id}", params={"companyId": company_id}, headers=auth_headers)

    assert (await db_session.execute(select(Ledger).where(Ledger.company_id == company_id))).scalars().all() == []
    assert (await db_session.execute(select(Voucher).where(Voucher.company_id == company_id))).scalars().all() == []
    assert (await db_session.execute(select(JournalItem).where(JournalItem.company_id == company_id))).scalars().all() == []

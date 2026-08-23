"""Phase 7D (Document Template & Rendering Architecture) tests. Uses the same conftest fixtures
(`client`, `auth_headers`, `db_session`) as every other test file. `_seed_company`/`_seed_groups`
insert rows directly via `db_session` - there is no CREATE_COMPANY/CREATE_GROUP sync operation
anywhere in this system, matching the precedent `test_reports_7c.py` already established."""
from __future__ import annotations

import json
import time
import uuid

import pytest
from httpx import AsyncClient

pytestmark = pytest.mark.asyncio


def _batch(company_id: str, events: list[dict]) -> dict:
    return {
        "companyId": company_id,
        "deviceId": "DEV_TEST",
        "items": [
            {
                "syncId": uuid.uuid4().hex,
                "entityType": e["aggregateType"],
                "entityId": e["aggregateId"],
                "operation": e["operation"],
                "payloadJson": json.dumps(e),
                "idempotencyKey": e["idempotencyKey"],
                "version": 1,
                "clientTimestamp": int(time.time() * 1000),
            }
            for e in events
        ],
    }


def _event(company_id: str, operation: str, aggregate_type: str, aggregate_id: str, **extra) -> dict:
    return {
        "schemaVersion": 1, "eventId": uuid.uuid4().hex, "idempotencyKey": uuid.uuid4().hex,
        "companyId": company_id, "financialYearId": extra.pop("financialYearId", ""),
        "operation": operation, "aggregateType": aggregate_type, "aggregateId": aggregate_id,
        **extra,
    }


def _create_ledger_event(company_id: str, ledger_id: str, group_id: str, name: str) -> dict:
    return _event(
        company_id, "CREATE_LEDGER", "LEDGER", ledger_id,
        ledger={
            "ledgerId": ledger_id, "groupId": group_id, "name": name, "code": "",
            "openingBalancePaise": 0, "openingBalanceType": "DEBIT", "gstin": "27BBBBB1111B1Z5", "pan": "",
            "stateCode": "27", "hsnSacCode": "", "defaultTaxRate": 0.0,
        },
    )


def _create_party_event(company_id: str, party_id: str, ledger_id: str, role: str) -> dict:
    return _event(
        company_id, "CREATE_PARTY", "PARTY", party_id,
        party={
            "partyId": party_id, "ledgerId": ledger_id, "role": role, "entityType": "BUSINESS",
            "displayName": f"Test {role.title()}", "contactName": "", "creditLimitPaise": None,
            "paymentTermsType": "NET_30", "paymentTermsCustomDays": None, "isActive": True,
        },
    )


def _sales_invoice_event_with_gst(
    company_id: str, voucher_id: str, date: str, debtors_ledger: str, sales_ledger: str,
    taxable_paise: int, cgst_paise: int, sgst_paise: int,
) -> dict:
    total_paise = taxable_paise + cgst_paise + sgst_paise
    return _event(
        company_id, "POST_SALES_INVOICE", "VOUCHER", voucher_id, financialYearId="FY_TEST",
        voucher={
            "voucherId": voucher_id, "voucherNumber": f"INV-{voucher_id}", "voucherType": "SALES",
            "date": date, "referenceNumber": "", "narration": "", "totalAmountPaise": total_paise,
            "isCancelled": False, "createdBy": "TESTER", "partyGstin": "", "isGstApplicable": True,
            "referenceVoucherId": None, "paymentMode": "",
        },
        journalLines=[
            {"itemId": uuid.uuid4().hex, "ledgerId": debtors_ledger, "ledgerName": "", "type": "DEBIT", "amountPaise": total_paise, "narration": "", "lineOrder": 1},
            {"itemId": uuid.uuid4().hex, "ledgerId": sales_ledger, "ledgerName": "", "type": "CREDIT", "amountPaise": total_paise, "narration": "", "lineOrder": 2},
        ],
        stockLines=[],
        gstTransactions=[{
            "gstTransactionId": uuid.uuid4().hex, "partyLedgerId": debtors_ledger, "partyGstin": "27BBBBB1111B1Z5",
            "placeOfSupply": "27", "supplyType": "INTRA_STATE", "itemId": "ITEM_1", "hsnSacCode": "8471",
            "quantityRaw": 1000, "taxableAmountPaise": taxable_paise, "gstRatePercent": 18.0,
            "cgstPaise": cgst_paise, "sgstPaise": sgst_paise, "igstPaise": 0, "cessPaise": 0,
            "direction": "OUTPUT", "lineOrder": 1,
        }],
        settlements=[],
    )


def _draft_invoice_event(company_id: str, invoice_id: str, party_id: str, invoice_type: str, date: str, invoice_number: str) -> dict:
    return _event(
        company_id, "CREATE_DRAFT_INVOICE", "INVOICE", invoice_id, financialYearId="FY_TEST",
        invoice={
            "invoiceId": invoice_id, "invoiceType": invoice_type, "invoiceNumber": invoice_number,
            "partyId": party_id, "date": date, "dueDate": None, "voucherId": None,
            "referenceInvoiceId": None, "sourceTradeDocumentId": None, "narration": "",
            "lines": [{
                "lineId": uuid.uuid4().hex, "itemId": "ITEM_1", "itemName": "Widget", "hsnSacCode": "8471",
                "quantityRaw": 1000, "ratePaise": 50_000_00, "gstRatePercent": 18.0, "cessRatePercent": 0.0, "lineOrder": 1,
            }],
        },
    )


def _link_invoice_event(company_id: str, invoice_id: str, voucher_id: str) -> dict:
    return _event(company_id, "LINK_INVOICE_VOUCHER", "INVOICE", invoice_id, invoice={"invoiceId": invoice_id, "voucherId": voucher_id})


async def _seed_company(db_session, company_id: str) -> None:
    from app.infrastructure.database.models import Company

    db_session.add(Company(
        company_id=company_id, name="Apex Traders", trade_name="Apex Traders", gstin="27AAAAA0000A1Z5",
        pan="AAAAA0000A", state_code="27", state_name="Maharashtra", email="apex@example.com", phone="9990001111",
        address="1 Market Street, Mumbai", currency="INR", financial_year_start_month=4,
        accounting_mode="ACCOUNT_ONLY", business_type="TRADING", is_default=True, created_at=0,
    ))
    await db_session.commit()


async def _seed_groups(db_session, company_id: str, groups: list[tuple[str, str]]) -> None:
    from app.infrastructure.database.models import Group

    db_session.add_all([
        Group(group_id=group_id, company_id=company_id, name=group_id, primary_group=primary_group, parent_group_id=None, is_system=True, affects_gross_profit=False, display_order=0)
        for group_id, primary_group in groups
    ])
    await db_session.commit()


async def _setup_posted_sales_invoice(client: AsyncClient, auth_headers: dict, db_session, company_id: str, suffix: str) -> tuple[str, str]:
    """Returns (invoice_id, voucher_id) for a fully posted Sales Invoice with real GstTransaction
    rows - the exact server-side shape `assemble_document_data` needs for its posted-document path."""
    debtors, sales = f"LED_DEBTOR_{suffix}", f"LED_SALES_{suffix}"
    await _seed_company(db_session, company_id)
    await _seed_groups(db_session, company_id, [(f"GRP_DEBTOR_{suffix}", "ASSETS"), (f"GRP_SALES_{suffix}", "INCOME")])
    party_id, invoice_id, voucher_id = f"PARTY_{suffix}", f"INV_{suffix}", f"V_{suffix}"

    batch = _batch(company_id, [
        _create_ledger_event(company_id, debtors, f"GRP_DEBTOR_{suffix}", "Debtors"),
        _create_ledger_event(company_id, sales, f"GRP_SALES_{suffix}", "Sales"),
        _create_party_event(company_id, party_id, debtors, "CUSTOMER"),
        _sales_invoice_event_with_gst(company_id, voucher_id, "2026-05-10", debtors, sales, 100_000_00, 9_000_00, 9_000_00),
        _draft_invoice_event(company_id, invoice_id, party_id, "SALES_INVOICE", "2026-05-10", "SI-2026-0001"),
        _link_invoice_event(company_id, invoice_id, voucher_id),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": f"batch-{suffix}"})
    assert resp.status_code == 200
    assert resp.json()["processedCount"] == 6
    return invoice_id, voucher_id


# ==========================================
# Document assembly
# ==========================================
async def test_assemble_document_data_posted_sales_invoice_uses_gst_transaction_values(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D1"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "D1")

    resp = await client.get("/documents/" + invoice_id, params={"companyId": company_id, "documentType": "SALES_INVOICE"}, headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["isPosted"] is True
    assert data["accountingVoucherNumber"] == f"INV-{voucher_id}"
    assert data["documentNumber"] == "SI-2026-0001"
    assert data["buyer"]["name"] == "Test Customer"
    assert data["seller"]["name"] == "Apex Traders"
    assert data["items"][0]["taxableAmountPaise"] == 100_000_00
    assert data["items"][0]["cgstPaise"] == 9_000_00
    assert data["items"][0]["sgstPaise"] == 9_000_00
    assert data["totals"]["grandTotalPaise"] == 118_000_00
    assert data["totals"]["roundOffPaise"] == 0


async def test_assemble_document_data_draft_invoice_returns_explicit_not_available(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D2"
    await _seed_company(db_session, company_id)
    await _seed_groups(db_session, company_id, [("GRP_DEBTOR_D2", "ASSETS")])
    party_id, invoice_id = "PARTY_D2", "INV_D2"
    batch = _batch(company_id, [
        _create_ledger_event(company_id, "LED_DEBTOR_D2", "GRP_DEBTOR_D2", "Debtors"),
        _create_party_event(company_id, party_id, "LED_DEBTOR_D2", "CUSTOMER"),
        _draft_invoice_event(company_id, invoice_id, party_id, "SALES_INVOICE", "2026-05-11", "SI-2026-0002"),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "batch-d2"})
    assert resp.status_code == 200

    resp = await client.get("/documents/" + invoice_id, params={"companyId": company_id, "documentType": "SALES_INVOICE"}, headers=auth_headers)
    assert resp.status_code == 409
    assert resp.json()["code"] == "DOCUMENT_PREVIEW_NOT_AVAILABLE"


async def test_get_document_pdf_reports_not_available_server_side(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D3"
    invoice_id, _ = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "D3")
    resp = await client.get("/documents/" + invoice_id + "/pdf", params={"companyId": company_id, "documentType": "SALES_INVOICE"}, headers=auth_headers)
    assert resp.status_code == 501
    assert resp.json()["code"] == "PDF_NOT_AVAILABLE_SERVER_SIDE"


# ==========================================
# Templates - versioning, default, isolation
# ==========================================
async def test_create_and_update_document_template_versioning_archives_previous_version(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D4"
    await _seed_company(db_session, company_id)
    create_resp = await client.post("/templates", json={
        "companyId": company_id, "documentType": "SALES_INVOICE", "templateName": "Modern Blue",
        "config": {"colors": {"primary": "#000000"}}, "isDefault": True,
    }, headers=auth_headers)
    assert create_resp.status_code == 200
    v1 = create_resp.json()
    assert v1["version"] == 1
    assert v1["status"] == "ACTIVE"

    update_resp = await client.put(f"/templates/{v1['templateId']}", json={
        "companyId": company_id, "config": {"colors": {"primary": "#FFFFFF"}},
    }, headers=auth_headers)
    assert update_resp.status_code == 200
    v2 = update_resp.json()
    assert v2["version"] == 2
    assert v2["config"]["colors"]["primary"] == "#FFFFFF"

    archived_v1_resp = await client.get(f"/templates/{v1['templateId']}", params={"companyId": company_id, "version": 1}, headers=auth_headers)
    assert archived_v1_resp.status_code == 200
    archived_v1 = archived_v1_resp.json()
    assert archived_v1["status"] == "ARCHIVED"
    assert archived_v1["config"]["colors"]["primary"] == "#000000"


async def test_document_templates_are_company_scoped_cross_company_access_rejected(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D5"
    await _seed_company(db_session, company_id)
    create_resp = await client.post("/templates", json={
        "companyId": company_id, "documentType": "SALES_INVOICE", "templateName": "Classic", "config": {},
    }, headers=auth_headers)
    template_id = create_resp.json()["templateId"]

    # User B has no membership row for COMP_D5 at all (COMP_D5 already has an owner - user A) -
    # require_company_access must reject this at the tenant-membership check itself, before ever
    # reaching the template lookup.
    user_b_resp = await client.post("/auth/register", json={"email": "userb_d5@example.com", "password": "correct horse battery staple"})
    headers_b = {"Authorization": f"Bearer {user_b_resp.json()['accessToken']}"}

    update_resp = await client.put(f"/templates/{template_id}", json={"companyId": company_id, "templateName": "Hijacked"}, headers=headers_b)
    assert update_resp.status_code == 403
    assert update_resp.json()["code"] == "TENANT_MISMATCH"

    # A *different*, genuinely-owned company (not COMP_D5) passed in the request body must not be
    # able to reach/mutate COMP_D5's template either - the company-scoped lookup itself finds
    # nothing under the caller's own company_id, independent of the membership check above.
    other_company_id = "COMP_D5_OTHER"
    claim_resp = await client.post("/sync/outbox/batch", json=_batch(other_company_id, []), headers={**headers_b, "Idempotency-Key": "claim-d5"})
    assert claim_resp.status_code == 200
    scoped_miss_resp = await client.put(f"/templates/{template_id}", json={"companyId": other_company_id, "templateName": "Hijacked"}, headers=headers_b)
    assert scoped_miss_resp.status_code == 400
    assert scoped_miss_resp.json()["code"] == "VALIDATION_ERROR"


async def test_create_document_template_rejects_blank_name(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D6"
    await _seed_company(db_session, company_id)
    resp = await client.post("/templates", json={"companyId": company_id, "documentType": "SALES_INVOICE", "templateName": "   "}, headers=auth_headers)
    assert resp.status_code == 400
    assert resp.json()["code"] == "VALIDATION_ERROR"


# ==========================================
# Branding - Business/Individual profile, assets
# ==========================================
async def test_business_profile_upsert_and_document_uses_it_for_seller_and_terms(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D7"
    invoice_id, _ = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "D7")

    asset_resp = await client.post("/document-assets", json={
        "companyId": company_id, "type": "LOGO", "storageReference": "documents/logo.png", "checksum": "chk", "mimeType": "image/png", "sizeBytes": 2048,
    }, headers=auth_headers)
    asset_id = asset_resp.json()["assetId"]

    profile_resp = await client.put("/business-profile", json={
        "companyId": company_id, "businessName": "Apex Traders Pvt Ltd", "logoAssetId": asset_id,
        "bankName": "HDFC Bank", "upiId": "apex@hdfcbank", "termsAndConditions": "Goods once sold will not be taken back.",
    }, headers=auth_headers)
    assert profile_resp.status_code == 200
    assert profile_resp.json()["businessName"] == "Apex Traders Pvt Ltd"

    doc_resp = await client.get("/documents/" + invoice_id, params={"companyId": company_id, "documentType": "SALES_INVOICE"}, headers=auth_headers)
    data = doc_resp.json()
    assert data["seller"]["name"] == "Apex Traders Pvt Ltd"
    assert data["branding"]["logoStorageReference"] == "documents/logo.png"
    assert data["paymentInformation"]["bankName"] == "HDFC Bank"
    assert data["terms"] == "Goods once sold will not be taken back."


async def test_individual_profile_separate_from_business_profile(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D8"
    await _seed_company(db_session, company_id)
    await client.put("/business-profile", json={"companyId": company_id, "businessName": "Apex Traders Pvt Ltd"}, headers=auth_headers)
    ind_resp = await client.put("/individual-profile", json={"companyId": company_id, "name": "Ramesh Kumar", "pan": "AAAAA1234A"}, headers=auth_headers)
    assert ind_resp.status_code == 200
    assert ind_resp.json()["name"] == "Ramesh Kumar"

    biz_get = await client.get("/business-profile", params={"companyId": company_id}, headers=auth_headers)
    assert biz_get.json()["businessName"] == "Apex Traders Pvt Ltd"
    ind_get = await client.get("/individual-profile", params={"companyId": company_id}, headers=auth_headers)
    assert ind_get.json()["name"] == "Ramesh Kumar"


async def test_document_assets_are_company_scoped(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D9"
    await _seed_company(db_session, company_id)
    asset_resp = await client.post("/document-assets", json={
        "companyId": company_id, "type": "LOGO", "storageReference": "documents/logo.png",
    }, headers=auth_headers)
    asset_id = asset_resp.json()["assetId"]

    # User B has no membership row for COMP_D9 at all - rejected at the tenant-membership check.
    user_b_resp = await client.post("/auth/register", json={"email": "userb_d9@example.com", "password": "correct horse battery staple"})
    headers_b = {"Authorization": f"Bearer {user_b_resp.json()['accessToken']}"}
    tenant_mismatch_resp = await client.get(f"/document-assets/{asset_id}", params={"companyId": company_id}, headers=headers_b)
    assert tenant_mismatch_resp.status_code == 403
    assert tenant_mismatch_resp.json()["code"] == "TENANT_MISMATCH"

    # A different, genuinely-owned company must still find nothing - the asset row simply isn't
    # scoped under that company_id, independent of the membership check above.
    other_company_id = "COMP_D9_OTHER"
    claim_resp = await client.post("/sync/outbox/batch", json=_batch(other_company_id, []), headers={**headers_b, "Idempotency-Key": "claim-d9"})
    assert claim_resp.status_code == 200
    scoped_miss_resp = await client.get(f"/document-assets/{asset_id}", params={"companyId": other_company_id}, headers=headers_b)
    assert scoped_miss_resp.status_code == 400
    assert scoped_miss_resp.json()["code"] == "VALIDATION_ERROR"


# ==========================================
# Historical integrity
# ==========================================
async def test_editing_template_does_not_change_accounting_or_past_render_record(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_D10"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "D10")

    create_resp = await client.post("/templates", json={
        "companyId": company_id, "documentType": "SALES_INVOICE", "templateName": "Classic", "config": {"layout": {"showLogo": True}}, "isDefault": True,
    }, headers=auth_headers)
    template_id = create_resp.json()["templateId"]

    from app.infrastructure.database.models import Ledger
    from sqlalchemy import select
    ledgers_before = {l.ledger_id: l.current_balance_paise for l in (await db_session.execute(select(Ledger).where(Ledger.company_id == company_id))).scalars().all()}

    first_render = await client.get("/documents/" + invoice_id + "/json", params={"companyId": company_id, "documentType": "SALES_INVOICE", "templateId": template_id}, headers=auth_headers)
    assert first_render.status_code == 200

    await client.put(f"/templates/{template_id}", json={"companyId": company_id, "config": {"layout": {"showLogo": False}}}, headers=auth_headers)

    v1_after_edit = await client.get(f"/templates/{template_id}", params={"companyId": company_id, "version": 1}, headers=auth_headers)
    assert v1_after_edit.json()["config"]["layout"]["showLogo"] is True

    ledgers_after = {l.ledger_id: l.current_balance_paise for l in (await db_session.execute(select(Ledger).where(Ledger.company_id == company_id))).scalars().all()}
    assert ledgers_before == ledgers_after

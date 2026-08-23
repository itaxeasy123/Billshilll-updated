"""Phase 7E (Export Architecture & Data Interchange) tests. Uses the same conftest fixtures
(`client`, `auth_headers`, `db_session`) as every other test file; `_seed_company`/`_seed_groups`
insert rows directly via `db_session`, matching the precedent `test_reports_7c.py`/
`test_documents_7d.py` already established."""
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
            "openingBalancePaise": 0, "openingBalanceType": "DEBIT", "gstin": "27AAAAA0000A1Z5", "pan": "",
            "stateCode": "27", "hsnSacCode": "", "defaultTaxRate": 0.0,
        },
    )


def _create_party_event(company_id: str, party_id: str, ledger_id: str, role: str) -> dict:
    return _event(
        company_id, "CREATE_PARTY", "PARTY", party_id,
        party={
            "partyId": party_id, "ledgerId": ledger_id, "role": role, "entityType": "BUSINESS",
            "displayName": "Beta Buyers, Ünicode", "contactName": "", "creditLimitPaise": None,
            "paymentTermsType": "NET_30", "paymentTermsCustomDays": None, "isActive": True,
        },
    )


def _sales_invoice_event_with_gst(
    company_id: str, voucher_id: str, date: str, debtors_ledger: str, sales_ledger: str,
    taxable_paise: int, cgst_paise: int, sgst_paise: int, narration: str = "",
) -> dict:
    total_paise = taxable_paise + cgst_paise + sgst_paise
    return _event(
        company_id, "POST_SALES_INVOICE", "VOUCHER", voucher_id, financialYearId="FY_TEST",
        voucher={
            "voucherId": voucher_id, "voucherNumber": f"INV-{voucher_id}", "voucherType": "SALES",
            "date": date, "referenceNumber": "", "narration": narration, "totalAmountPaise": total_paise,
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
    debtors, sales = f"LED_DEBTOR_{suffix}", f"LED_SALES_{suffix}"
    await _seed_company(db_session, company_id)
    await _seed_groups(db_session, company_id, [(f"GRP_DEBTOR_{suffix}", "ASSETS"), (f"GRP_SALES_{suffix}", "INCOME")])
    party_id, invoice_id, voucher_id = f"PARTY_{suffix}", f"INV_{suffix}", f"V_{suffix}"

    batch = _batch(company_id, [
        _create_ledger_event(company_id, debtors, f"GRP_DEBTOR_{suffix}", "Debtors"),
        _create_ledger_event(company_id, sales, f"GRP_SALES_{suffix}", "Sales"),
        _create_party_event(company_id, party_id, debtors, "CUSTOMER"),
        _sales_invoice_event_with_gst(company_id, voucher_id, "2026-05-10", debtors, sales, 100_000_00, 9_000_00, 9_000_00, narration='Sale, with "quotes"'),
        _draft_invoice_event(company_id, invoice_id, party_id, "SALES_INVOICE", "2026-05-10", "SI-2026-0001"),
        _link_invoice_event(company_id, invoice_id, voucher_id),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": f"batch-{suffix}"})
    assert resp.status_code == 200
    assert resp.json()["processedCount"] == 6
    return invoice_id, voucher_id


# ==========================================
# Voucher export - JSON/CSV, exact paise, security, integrity
# ==========================================
async def test_export_voucher_json_wraps_versioned_envelope_and_exact_paise(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E1"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E1")

    resp = await client.get(f"/exports/vouchers/{voucher_id}", params={"companyId": company_id}, headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["schemaVersion"] == "1.0"
    assert body["exportType"] == "VOUCHER"
    assert body["companyId"] == company_id
    assert body["data"]["totalAmountPaise"] == 118_000_00
    assert len(body["data"]["journalLines"]) == 2
    # Never a floating-point representation of the authoritative amount.
    assert isinstance(body["data"]["totalAmountPaise"], int)


async def test_export_voucher_csv_escapes_quotes_and_unicode(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E2"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E2")

    resp = await client.get(f"/exports/vouchers/{voucher_id}", params={"companyId": company_id, "format": "csv"}, headers=auth_headers)
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/csv")
    text = resp.text
    assert text.startswith("voucherId,voucherNumber")
    assert '"Sale, with ""quotes"""' in text
    assert len(text.strip().split("\r\n")) == 3  # header + 2 journal lines


async def test_export_voucher_missing_resource_returns_404(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E3"
    await _seed_company(db_session, company_id)
    resp = await client.get("/exports/vouchers/DOES_NOT_EXIST", params={"companyId": company_id}, headers=auth_headers)
    assert resp.status_code == 404
    assert resp.json()["code"] == "RESOURCE_NOT_FOUND"


async def test_export_voucher_unauthorized_no_token_returns_401(client: AsyncClient, db_session):
    resp = await client.get("/exports/vouchers/V1", params={"companyId": "COMP_E4"})
    assert resp.status_code == 401


async def test_export_voucher_cross_tenant_returns_403(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E5"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E5")

    user_b_resp = await client.post("/auth/register", json={"email": "userb_e5@example.com", "password": "correct horse battery staple"})
    headers_b = {"Authorization": f"Bearer {user_b_resp.json()['accessToken']}"}
    resp = await client.get(f"/exports/vouchers/{voucher_id}", params={"companyId": company_id}, headers=headers_b)
    assert resp.status_code == 403
    assert resp.json()["code"] == "TENANT_MISMATCH"


async def test_export_voucher_invalid_format_returns_400(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E6"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E6")
    resp = await client.get(f"/exports/vouchers/{voucher_id}", params={"companyId": company_id, "format": "gstr-json"}, headers=auth_headers)
    assert resp.status_code == 400
    assert resp.json()["code"] == "EXPORT_FORMAT_UNSUPPORTED"


async def test_export_never_mutates_database_state(client: AsyncClient, auth_headers: dict, db_session):
    from sqlalchemy import select
    from app.infrastructure.database.models import Ledger

    company_id = "COMP_E7"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E7")
    ledgers_before = {l.ledger_id: l.current_balance_paise for l in (await db_session.execute(select(Ledger).where(Ledger.company_id == company_id))).scalars().all()}

    await client.get(f"/exports/vouchers/{voucher_id}", params={"companyId": company_id}, headers=auth_headers)
    await client.get(f"/exports/vouchers/{voucher_id}", params={"companyId": company_id, "format": "csv"}, headers=auth_headers)
    await client.get(f"/exports/invoices/{invoice_id}", params={"companyId": company_id, "documentType": "SALES_INVOICE"}, headers=auth_headers)
    await client.get("/exports/reports/trial-balance", params={"companyId": company_id, "financialYearId": "FY_TEST"}, headers=auth_headers)

    ledgers_after = {l.ledger_id: l.current_balance_paise for l in (await db_session.execute(select(Ledger).where(Ledger.company_id == company_id))).scalars().all()}
    assert ledgers_before == ledgers_after


# ==========================================
# Party / Ledger export
# ==========================================
async def test_export_party_and_ledger(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E8"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E8")

    party_resp = await client.get("/exports/parties/PARTY_E8", params={"companyId": company_id}, headers=auth_headers)
    assert party_resp.status_code == 200
    assert party_resp.json()["data"]["displayName"] == "Beta Buyers, Ünicode"

    ledger_resp = await client.get("/exports/ledgers/LED_DEBTOR_E8", params={"companyId": company_id, "format": "csv"}, headers=auth_headers)
    assert ledger_resp.status_code == 200
    assert "LED_DEBTOR_E8" in ledger_resp.text


# ==========================================
# Invoice export
# ==========================================
async def test_export_invoice_matches_assembled_document_data(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E9"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E9")

    resp = await client.get(f"/exports/invoices/{invoice_id}", params={"companyId": company_id, "documentType": "SALES_INVOICE"}, headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["cgstPaise"] == 9_000_00
    assert data["grandTotalPaise"] == 118_000_00
    assert data["isPosted"] is True


async def test_export_draft_invoice_returns_preview_not_available(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E10"
    await _seed_company(db_session, company_id)
    await _seed_groups(db_session, company_id, [("GRP_DEBTOR_E10", "ASSETS")])
    batch = _batch(company_id, [
        _create_ledger_event(company_id, "LED_DEBTOR_E10", "GRP_DEBTOR_E10", "Debtors"),
        _create_party_event(company_id, "PARTY_E10", "LED_DEBTOR_E10", "CUSTOMER"),
        _draft_invoice_event(company_id, "INV_E10", "PARTY_E10", "SALES_INVOICE", "2026-05-11", "SI-2026-0002"),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "batch-e10"})
    assert resp.status_code == 200

    export_resp = await client.get("/exports/invoices/INV_E10", params={"companyId": company_id, "documentType": "SALES_INVOICE"}, headers=auth_headers)
    assert export_resp.status_code == 409
    assert export_resp.json()["code"] == "DOCUMENT_PREVIEW_NOT_AVAILABLE"


# ==========================================
# Report exports
# ==========================================
async def test_export_trial_balance_deterministic_across_repeated_calls(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E11"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E11")

    first = await client.get("/exports/reports/trial-balance", params={"companyId": company_id, "financialYearId": "FY_TEST"}, headers=auth_headers)
    second = await client.get("/exports/reports/trial-balance", params={"companyId": company_id, "financialYearId": "FY_TEST"}, headers=auth_headers)
    assert first.json()["data"] == second.json()["data"]


async def test_export_outstanding_csv_empty_dataset_produces_header_only(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E12"
    await _seed_company(db_session, company_id)
    resp = await client.get("/exports/reports/outstanding", params={"companyId": company_id, "format": "csv"}, headers=auth_headers)
    assert resp.status_code == 200
    assert resp.text == "invoiceId,invoiceNumber,invoiceType,partyId,partyName,voucherNumber,date,dueDate,totalAmountPaise,outstandingAmountPaise,status,daysOutstanding,agingBucket\r\n"


async def test_export_profit_loss_rejects_csv_format(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E13"
    await _seed_company(db_session, company_id)
    resp = await client.get("/exports/reports/profit-loss", params={"companyId": company_id, "financialYearId": "FY_TEST", "format": "csv"}, headers=auth_headers)
    assert resp.status_code == 400
    assert resp.json()["code"] == "EXPORT_FORMAT_UNSUPPORTED"


# ==========================================
# GST transaction export / GSTR JSON
# ==========================================
async def test_export_gst_gstr_json_groups_by_direction(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E14"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E14")

    resp = await client.get("/exports/gst", params={"companyId": company_id, "financialYearId": "FY_TEST", "format": "gstr-json"}, headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert len(data["outwardSupplies"]) == 1
    assert data["outwardSupplies"][0]["hsnSacCode"] == "8471"
    assert data["outwardSupplies"][0]["placeOfSupply"] == "27"
    assert data["outwardSupplies"][0]["isService"] is None
    assert data["totals"]["totalTaxableOutwardPaise"] == 100_000_00


async def test_export_gst_transactions_csv_deterministic_columns(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_E15"
    invoice_id, voucher_id = await _setup_posted_sales_invoice(client, auth_headers, db_session, company_id, "E15")

    resp = await client.get("/exports/gst/transactions", params={"companyId": company_id, "financialYearId": "FY_TEST", "format": "csv"}, headers=auth_headers)
    assert resp.status_code == 200
    header = resp.text.splitlines()[0]
    assert header == "gstTransactionId,voucherId,voucherType,partyGstin,placeOfSupply,supplyType,hsnSacCode,isService,taxableAmountPaise,gstRatePercent,cgstPaise,sgstPaise,igstPaise,cessPaise,direction,lineOrder"

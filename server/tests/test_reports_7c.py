"""Phase 7C (Report Management) tests - Trial Balance/GST Summary additive extensions, and the
new Profit & Loss/Balance Sheet/Ledger Statement/Day Book/Outstanding/Receivables/Payables/Cash
Flow/Ratio Analysis report endpoints. Uses the same conftest fixtures (`client`, `auth_headers`,
`db_session`) as every other test file."""
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
            "openingBalancePaise": 0, "openingBalanceType": "DEBIT", "gstin": "", "pan": "",
            "stateCode": "", "hsnSacCode": "", "defaultTaxRate": 0.0,
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


def _journal_event(company_id: str, voucher_id: str, date: str, debit_ledger: str, credit_ledger: str, amount_paise: int) -> dict:
    return _event(
        company_id, "POST_JOURNAL", "VOUCHER", voucher_id, financialYearId="FY_TEST",
        voucher={
            "voucherId": voucher_id, "voucherNumber": f"JRN-{voucher_id}", "voucherType": "JOURNAL",
            "date": date, "referenceNumber": "", "narration": "Test journal", "totalAmountPaise": amount_paise,
            "isCancelled": False, "createdBy": "TESTER", "partyGstin": "", "isGstApplicable": False,
            "referenceVoucherId": None, "paymentMode": "",
        },
        journalLines=[
            {"itemId": uuid.uuid4().hex, "ledgerId": debit_ledger, "ledgerName": "", "type": "DEBIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 1},
            {"itemId": uuid.uuid4().hex, "ledgerId": credit_ledger, "ledgerName": "", "type": "CREDIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 2},
        ],
        stockLines=[], gstTransactions=[], settlements=[],
    )


def _sales_invoice_event(company_id: str, voucher_id: str, date: str, debtors_ledger: str, sales_ledger: str, amount_paise: int) -> dict:
    return _event(
        company_id, "POST_SALES_INVOICE", "VOUCHER", voucher_id, financialYearId="FY_TEST",
        voucher={
            "voucherId": voucher_id, "voucherNumber": f"INV-{voucher_id}", "voucherType": "SALES",
            "date": date, "referenceNumber": "", "narration": "", "totalAmountPaise": amount_paise,
            "isCancelled": False, "createdBy": "TESTER", "partyGstin": "", "isGstApplicable": False,
            "referenceVoucherId": None, "paymentMode": "",
        },
        journalLines=[
            {"itemId": uuid.uuid4().hex, "ledgerId": debtors_ledger, "ledgerName": "", "type": "DEBIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 1},
            {"itemId": uuid.uuid4().hex, "ledgerId": sales_ledger, "ledgerName": "", "type": "CREDIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 2},
        ],
        stockLines=[], gstTransactions=[], settlements=[],
    )


def _draft_invoice_event(company_id: str, invoice_id: str, party_id: str, invoice_type: str, date: str, due_date: str | None, invoice_number: str) -> dict:
    return _event(
        company_id, "CREATE_DRAFT_INVOICE", "INVOICE", invoice_id, financialYearId="FY_TEST",
        invoice={
            "invoiceId": invoice_id, "invoiceType": invoice_type, "invoiceNumber": invoice_number,
            "partyId": party_id, "date": date, "dueDate": due_date, "voucherId": None,
            "referenceInvoiceId": None, "sourceTradeDocumentId": None, "narration": "",
            "lines": [{
                "lineId": uuid.uuid4().hex, "itemId": "ITEM_1", "itemName": "Widget", "hsnSacCode": "8471",
                "quantityRaw": 1000, "ratePaise": 50_000, "gstRatePercent": 18.0, "cessRatePercent": 0.0, "lineOrder": 1,
            }],
        },
    )


def _link_invoice_event(company_id: str, invoice_id: str, voucher_id: str) -> dict:
    return _event(company_id, "LINK_INVOICE_VOUCHER", "INVOICE", invoice_id, invoice={"invoiceId": invoice_id, "voucherId": voucher_id})


async def _seed_groups(db_session, company_id: str, groups: list[tuple[str, str]]) -> None:
    """Seeds `account_groups` rows directly via the DB session, bypassing the Outbox entirely -
    there is no CREATE_GROUP sync operation anywhere in this system (Groups are seeded some other
    way on a real device; every other test file that needs a Ledger's groupId to resolve seeds it
    the same direct way). Each `groups` entry is `(group_id, primary_group)`; all seeded as
    top-level (no parent) - sufficient for `find_node` to locate them by exact id regardless of
    nesting depth."""
    from app.infrastructure.database.models import Group

    db_session.add_all([
        Group(group_id=group_id, company_id=company_id, name=group_id, primary_group=primary_group, parent_group_id=None, is_system=True, affects_gross_profit=False, display_order=0)
        for group_id, primary_group in groups
    ])
    await db_session.commit()


def _cancel_voucher_event(company_id: str, voucher_id: str, debit_ledger: str, credit_ledger: str, amount_paise: int) -> dict:
    return _event(
        company_id, "CANCEL_VOUCHER", "VOUCHER", voucher_id,
        voucher={
            "voucherId": voucher_id, "voucherNumber": "", "voucherType": "JOURNAL", "date": "2026-05-01",
            "referenceNumber": "", "narration": "", "totalAmountPaise": 0, "isCancelled": True,
            "createdBy": "TESTER", "partyGstin": "", "isGstApplicable": False, "referenceVoucherId": None, "paymentMode": "",
        },
        journalLines=[
            {"itemId": uuid.uuid4().hex, "ledgerId": credit_ledger, "ledgerName": "", "type": "CREDIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 1},
            {"itemId": uuid.uuid4().hex, "ledgerId": debit_ledger, "ledgerName": "", "type": "DEBIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 2},
        ],
        stockLines=[], gstTransactions=[], settlements=[],
    )


async def test_trial_balance_additive_fields_present_and_group_hierarchy_populated(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_R1"
    cash, sales = "LED_R1_CASH", "LED_R1_SALES"
    await _seed_groups(db_session, company_id, [("GRP_CASH_COMP_R1", "ASSETS"), ("GRP_SALES_COMP_R1", "INCOME")])
    batch = _batch(company_id, [
        _create_ledger_event(company_id, cash, "GRP_CASH_COMP_R1", "Cash"),
        _create_ledger_event(company_id, sales, "GRP_SALES_COMP_R1", "Sales"),
        _journal_event(company_id, "V_R1", "2026-05-01", cash, sales, 10_000_00),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "r1a"})
    assert resp.json()["processedCount"] == 3

    tb = (await client.get("/reports/trial-balance", params={"companyId": company_id, "financialYearId": "FY_TEST"}, headers=auth_headers)).json()
    # Pre-existing fields unchanged in meaning.
    row = next(r for r in tb["rows"] if r["ledgerId"] == cash)
    assert row["debitPaise"] == 10_000_00
    assert tb["isBalanced"] is True
    # New Phase 7C fields present.
    assert "openingDebitPaise" in row and "closingDebitPaise" in row
    assert isinstance(tb["groupHierarchy"], list) and len(tb["groupHierarchy"]) > 0


async def test_gst_summary_additive_net_payable_present(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_R2"
    tb = (await client.get("/reports/gst", params={"companyId": company_id, "financialYearId": "FY_TEST"}, headers=auth_headers)).json()
    assert tb["outward"]["taxablePaise"] == 0
    assert "netTaxPayablePaise" in tb and "netCessPayablePaise" in tb


async def test_profit_and_loss_classifies_sales_and_purchase(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_R3"
    sales, purchase, cash = "LED_R3_SALES", "LED_R3_PURCHASE", "LED_R3_CASH"
    await _seed_groups(db_session, company_id, [
        ("GRP_SALES_COMP_R3", "INCOME"), ("GRP_PURCHASE_COMP_R3", "EXPENSES"), ("GRP_CASH_COMP_R3", "ASSETS"),
    ])
    batch = _batch(company_id, [
        _create_ledger_event(company_id, sales, "GRP_SALES_COMP_R3", "Sales"),
        _create_ledger_event(company_id, purchase, "GRP_PURCHASE_COMP_R3", "Purchase"),
        _create_ledger_event(company_id, cash, "GRP_CASH_COMP_R3", "Cash"),
        _journal_event(company_id, "V_SALE_R3", "2026-05-01", cash, sales, 100_000_00),
        _journal_event(company_id, "V_PUR_R3", "2026-05-02", purchase, cash, 40_000_00),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "r3a"})
    assert resp.json()["processedCount"] == 5

    pnl = (await client.get("/reports/profit-loss", params={"companyId": company_id, "financialYearId": "FY_TEST"}, headers=auth_headers)).json()
    assert pnl["salesRevenuePaise"] == 100_000_00
    assert pnl["purchasesPaise"] == 40_000_00
    assert pnl["grossProfitPaise"] == 60_000_00
    assert pnl["netProfitPaise"] == 60_000_00
    assert pnl["isInventoryAware"] is False


async def test_balance_sheet_is_balanced(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_R4"
    capital, cash = "LED_R4_CAPITAL", "LED_R4_CASH"
    await _seed_groups(db_session, company_id, [("GRP_CAPITAL_COMP_R4", "EQUITY"), ("GRP_CASH_COMP_R4", "ASSETS")])
    batch = _batch(company_id, [
        _create_ledger_event(company_id, capital, "GRP_CAPITAL_COMP_R4", "Capital"),
        _create_ledger_event(company_id, cash, "GRP_CASH_COMP_R4", "Cash"),
        _journal_event(company_id, "V_CAP_R4", "2026-04-05", cash, capital, 500_000_00),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "r4a"})
    assert resp.json()["processedCount"] == 3

    bs = (await client.get("/reports/balance-sheet", params={"companyId": company_id, "financialYearId": "FY_TEST"}, headers=auth_headers)).json()
    assert bs["isBalanced"] is True
    assert bs["totalLiabilitiesPaise"] == bs["totalAssetsPaise"]
    assert bs["cashInHandPaise"] == 500_000_00
    assert bs["capitalAccountsPaise"] == 500_000_00


async def test_ledger_statement_running_balance(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_R5"
    cash, sales = "LED_R5_CASH", "LED_R5_SALES"
    batch = _batch(company_id, [
        _create_ledger_event(company_id, cash, "GRP_CASH_COMP_R5", "Cash"),
        _create_ledger_event(company_id, sales, "GRP_SALES_COMP_R5", "Sales"),
        _journal_event(company_id, "V1_R5", "2026-05-01", cash, sales, 1_000_00),
        _journal_event(company_id, "V2_R5", "2026-05-05", cash, sales, 2_000_00),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "r5a"})
    assert resp.json()["processedCount"] == 4

    stmt = (await client.get("/reports/ledger", params={"companyId": company_id, "ledgerId": cash}, headers=auth_headers)).json()
    assert len(stmt["rows"]) == 2
    assert stmt["rows"][0]["runningBalancePaise"] == 1_000_00
    assert stmt["rows"][1]["runningBalancePaise"] == 3_000_00
    assert stmt["closingBalancePaise"] == 3_000_00
    assert stmt["closingType"] == "DEBIT"


async def test_day_book_chronological_and_cancelled_status(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_R6"
    cash, sales = "LED_R6_CASH", "LED_R6_SALES"
    batch = _batch(company_id, [
        _create_ledger_event(company_id, cash, "GRP_CASH_COMP_R6", "Cash"),
        _create_ledger_event(company_id, sales, "GRP_SALES_COMP_R6", "Sales"),
        _journal_event(company_id, "V2_R6", "2026-05-10", cash, sales, 500_00),
        _journal_event(company_id, "V1_R6", "2026-05-05", cash, sales, 300_00),
    ])
    await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "r6a"})
    cancel_resp = await client.post(
        "/sync/outbox/batch",
        json=_batch(company_id, [_cancel_voucher_event(company_id, "V2_R6", cash, sales, 500_00)]),
        headers={**auth_headers, "Idempotency-Key": "r6b"},
    )
    assert cancel_resp.json()["processedCount"] == 1

    db = (await client.get("/reports/day-book", params={"companyId": company_id, "startDate": "2026-05-01", "endDate": "2026-05-31"}, headers=auth_headers)).json()
    assert [r["voucherId"] for r in db["rows"]] == ["V1_R6", "V2_R6"]
    assert db["rows"][0]["status"] == "POSTED"
    assert db["rows"][1]["status"] == "CANCELLED"
    assert db["totalAmountPaise"] == 300_00  # cancelled voucher excluded from the total


async def test_outstanding_receivables_payables_and_aging(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_R7"
    debtors, creditors = "LED_R7_DEBTORS", "LED_R7_CREDITORS"
    setup = _batch(company_id, [
        _create_ledger_event(company_id, debtors, "GRP_DEBTORS_COMP_R7", "Debtors"),
        _create_ledger_event(company_id, creditors, "GRP_CREDITORS_COMP_R7", "Creditors"),
        _create_party_event(company_id, "PTY_CUST_R7", debtors, "CUSTOMER"),
        _create_party_event(company_id, "PTY_SUPP_R7", creditors, "SUPPLIER"),
    ])
    await client.post("/sync/outbox/batch", json=setup, headers={**auth_headers, "Idempotency-Key": "r7a"})

    sale_event = _sales_invoice_event(company_id, "V_SALE_R7", "2026-05-01", debtors, "LED_R7_SALES", 100_000_00)
    draft_sale = _draft_invoice_event(company_id, "INV_SALE_R7", "PTY_CUST_R7", "SALES_INVOICE", "2026-05-01", "2026-04-01", "SI-2026-0001")
    link_sale = _link_invoice_event(company_id, "INV_SALE_R7", "V_SALE_R7")  # overdue: due date in the past
    await client.post(
        "/sync/outbox/batch",
        json=_batch(company_id, [_create_ledger_event(company_id, "LED_R7_SALES", "GRP_SALES_COMP_R7", "Sales"), draft_sale, sale_event, link_sale]),
        headers={**auth_headers, "Idempotency-Key": "r7b"},
    )

    receivables = (await client.get("/reports/receivables", params={"companyId": company_id}, headers=auth_headers)).json()
    assert len(receivables["rows"]) == 1
    assert receivables["rows"][0]["invoiceId"] == "INV_SALE_R7"
    assert receivables["rows"][0]["agingBucket"] != "CURRENT"  # due date is in the past

    payables = (await client.get("/reports/payables", params={"companyId": company_id}, headers=auth_headers)).json()
    assert payables["rows"] == []

    outstanding = (await client.get("/reports/outstanding", params={"companyId": company_id}, headers=auth_headers)).json()
    assert len(outstanding["rows"]) == 1
    assert outstanding["totalOutstandingPaise"] == 100_000_00


async def test_cash_flow_operating_equals_net_profit_with_no_working_capital_change(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_R8"
    cash, sales = "LED_R8_CASH", "LED_R8_SALES"
    await _seed_groups(db_session, company_id, [("GRP_CASH_COMP_R8", "ASSETS"), ("GRP_SALES_COMP_R8", "INCOME")])
    batch = _batch(company_id, [
        _create_ledger_event(company_id, cash, "GRP_CASH_COMP_R8", "Cash"),
        _create_ledger_event(company_id, sales, "GRP_SALES_COMP_R8", "Sales"),
        _journal_event(company_id, "V_R8", "2026-05-10", cash, sales, 25_000_00),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "r8a"})
    assert resp.json()["processedCount"] == 3

    cf = (await client.get(
        "/reports/cash-flow",
        params={"companyId": company_id, "financialYearId": "FY_TEST", "startDate": "2026-04-01", "endDate": "2026-05-31"},
        headers=auth_headers,
    )).json()
    assert cf["netProfitPaise"] == 25_000_00
    assert cf["changeInCurrentAssetsExcludingCashPaise"] == 0
    assert cf["changeInCurrentLiabilitiesPaise"] == 0
    assert cf["netCashFromOperatingActivitiesPaise"] == 25_000_00
    assert cf["netCashFromInvestingActivitiesPaise"] is None
    assert cf["netCashFromFinancingActivitiesPaise"] is None


async def test_ratio_analysis_current_ratio_and_zero_denominator(client: AsyncClient, auth_headers: dict, db_session):
    company_id = "COMP_R9"
    debtors, capital = "LED_R9_DEBTORS", "LED_R9_CAPITAL"
    await _seed_groups(db_session, company_id, [("GRP_DEBTORS_COMP_R9", "ASSETS"), ("GRP_CAPITAL_COMP_R9", "EQUITY")])
    batch = _batch(company_id, [
        _create_ledger_event(company_id, debtors, "GRP_DEBTORS_COMP_R9", "Debtors"),
        _create_ledger_event(company_id, capital, "GRP_CAPITAL_COMP_R9", "Capital"),
        _journal_event(company_id, "V_R9", "2026-04-05", debtors, capital, 200_000_00),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "r9a"})
    assert resp.json()["processedCount"] == 3

    ratios = (await client.get(
        "/reports/ratios", params={"companyId": company_id, "financialYearId": "FY_TEST"}, headers=auth_headers
    )).json()
    # No Creditors/Loans/Duties ledger exists in this scenario -> total current liabilities is
    # genuinely 0, so current ratio must safely return 0.0 rather than crash/Infinity.
    assert ratios["currentRatio"] == 0.0
    assert ratios["debtEquityRatio"] == 0.0


async def test_new_report_endpoints_require_authentication(client: AsyncClient):
    # Every new Phase 7C route wires the same `get_current_user_id`/`require_company_access`
    # dependency chain already thoroughly exercised cross-company in test_tenant_isolation.py -
    # this just confirms each new route actually declared that dependency (not skipped by mistake).
    for path in ["/reports/profit-loss", "/reports/balance-sheet", "/reports/ledger", "/reports/day-book",
                 "/reports/outstanding", "/reports/receivables", "/reports/payables", "/reports/cash-flow", "/reports/ratios"]:
        resp = await client.get(path, params={"companyId": "COMP_X", "financialYearId": "FY_X", "ledgerId": "L", "startDate": "2026-01-01", "endDate": "2026-01-31"})
        assert resp.status_code == 401, f"{path} did not require authentication"

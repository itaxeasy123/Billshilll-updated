"""Phase 7A (Party + Invoice Domain Foundation) tests - party creation as a thin Ledger extension,
draft invoices having zero accounting effect until posted, invoice<->voucher linking via a second
independent sync event (never touching `apply_voucher_event`), and status derivation across all
six InvoiceStatus states. Uses the same conftest fixtures (`client`, `auth_headers`, `db_session`)
as every other Phase 6A test file."""
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


def _create_party_event(company_id: str, party_id: str, ledger_id: str, role: str = "CUSTOMER") -> dict:
    return _event(
        company_id, "CREATE_PARTY", "PARTY", party_id,
        party={
            "partyId": party_id, "ledgerId": ledger_id, "role": role, "entityType": "BUSINESS",
            "displayName": "Test Party Pvt Ltd", "contactName": "Test Contact",
            "creditLimitPaise": None, "paymentTermsType": "NET_30", "paymentTermsCustomDays": None,
            "isActive": True,
        },
    )


def _draft_invoice_event(company_id: str, invoice_id: str, party_id: str, due_date: str | None = None, invoice_number: str = "SI-2026-0001") -> dict:
    # Phase 7B: invoiceNumber is assigned once, here, by Android's own generateNextDocumentNumber -
    # this test simulates that already-assigned value, since the server never generates it itself.
    return _event(
        company_id, "CREATE_DRAFT_INVOICE", "INVOICE", invoice_id, financialYearId="FY_TEST",
        invoice={
            "invoiceId": invoice_id, "invoiceType": "SALES_INVOICE", "invoiceNumber": invoice_number,
            "partyId": party_id, "date": "2026-05-01", "dueDate": due_date, "voucherId": None,
            "referenceInvoiceId": None, "narration": "",
            "lines": [{
                "lineId": uuid.uuid4().hex, "itemId": "ITEM_1", "itemName": "Widget",
                "hsnSacCode": "8471", "quantityRaw": 1000, "ratePaise": 50_000,
                "gstRatePercent": 18.0, "cessRatePercent": 0.0, "lineOrder": 1,
            }],
        },
    )


def _cancel_draft_invoice_event(company_id: str, invoice_id: str) -> dict:
    return _event(company_id, "CANCEL_DRAFT_INVOICE", "INVOICE", invoice_id, invoice={"invoiceId": invoice_id})


def _link_invoice_event(company_id: str, invoice_id: str, voucher_id: str) -> dict:
    # Phase 7B: only ever carries voucherId - invoiceNumber is never touched by this event.
    return _event(
        company_id, "LINK_INVOICE_VOUCHER", "INVOICE", invoice_id,
        invoice={"invoiceId": invoice_id, "voucherId": voucher_id},
    )


def _post_sales_invoice_event(company_id: str, voucher_id: str, party_ledger_id: str, amount_paise: int) -> dict:
    voucher_number = f"INV-{voucher_id}"
    return _event(
        company_id, "POST_SALES_INVOICE", "VOUCHER", voucher_id, financialYearId="FY_TEST",
        voucher={
            "voucherId": voucher_id, "voucherNumber": voucher_number, "voucherType": "SALES",
            "date": "2026-05-01", "referenceNumber": "", "narration": "", "totalAmountPaise": amount_paise,
            "isCancelled": False, "createdBy": "TESTER", "partyGstin": "", "isGstApplicable": False,
            "referenceVoucherId": None, "paymentMode": "",
        },
        journalLines=[
            {"itemId": uuid.uuid4().hex, "ledgerId": party_ledger_id, "ledgerName": "", "type": "DEBIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 1},
            {"itemId": uuid.uuid4().hex, "ledgerId": "LED_SALES_TEST", "ledgerName": "", "type": "CREDIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 2},
        ],
        stockLines=[], gstTransactions=[], settlements=[],
    ), voucher_number


def _cancel_voucher_event(company_id: str, voucher_id: str, party_ledger_id: str, amount_paise: int) -> dict:
    # CANCEL_VOUCHER must carry the compensating reversal journal lines (validate_balanced() runs
    # unconditionally before the operation branch, exactly mirroring Android's cancel() flow where
    # the reversal lines are already computed client-side and ride along in the same event).
    return _event(
        company_id, "CANCEL_VOUCHER", "VOUCHER", voucher_id,
        voucher={
            "voucherId": voucher_id, "voucherNumber": "", "voucherType": "SALES", "date": "2026-05-01",
            "referenceNumber": "", "narration": "", "totalAmountPaise": 0, "isCancelled": True,
            "createdBy": "TESTER", "partyGstin": "", "isGstApplicable": False, "referenceVoucherId": None,
            "paymentMode": "",
        },
        journalLines=[
            {"itemId": uuid.uuid4().hex, "ledgerId": "LED_SALES_TEST", "ledgerName": "", "type": "DEBIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 1},
            {"itemId": uuid.uuid4().hex, "ledgerId": party_ledger_id, "ledgerName": "", "type": "CREDIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 2},
        ],
        stockLines=[], gstTransactions=[], settlements=[],
    )


def _receipt_settlement_event(company_id: str, voucher_id: str, party_ledger_id: str, invoice_voucher_id: str, amount_paise: int) -> dict:
    return _event(
        company_id, "POST_RECEIPT", "VOUCHER", voucher_id, financialYearId="FY_TEST",
        voucher={
            "voucherId": voucher_id, "voucherNumber": f"RCT-{voucher_id}", "voucherType": "RECEIPT",
            "date": "2026-05-15", "referenceNumber": "", "narration": "", "totalAmountPaise": amount_paise,
            "isCancelled": False, "createdBy": "TESTER", "partyGstin": "", "isGstApplicable": False,
            "referenceVoucherId": None, "paymentMode": "BANK",
        },
        journalLines=[
            {"itemId": uuid.uuid4().hex, "ledgerId": "LED_BANK_TEST", "ledgerName": "", "type": "DEBIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 1},
            {"itemId": uuid.uuid4().hex, "ledgerId": party_ledger_id, "ledgerName": "", "type": "CREDIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 2},
        ],
        stockLines=[], gstTransactions=[],
        settlements=[{"allocationId": uuid.uuid4().hex, "settlementVoucherId": voucher_id, "invoiceVoucherId": invoice_voucher_id, "allocatedAmountPaise": amount_paise}],
    )


async def test_create_party_thin_extension_of_ledger(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_PARTY_1"
    ledger_id, party_id = "LED_PARTY_1", "PTY_1"
    batch = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_PARTY_1", "Acme Corp"),
        _create_party_event(company_id, party_id, ledger_id),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "b1"})
    assert resp.status_code == 200
    assert resp.json()["processedCount"] == 2

    parties = await client.get("/parties", params={"companyId": company_id}, headers=auth_headers)
    assert parties.status_code == 200
    body = parties.json()
    assert len(body) == 1
    assert body[0]["partyId"] == party_id
    assert body[0]["ledgerId"] == ledger_id
    assert body[0]["role"] == "CUSTOMER"


async def test_draft_invoice_has_zero_accounting_effect(client: AsyncClient, auth_headers: dict, db_session):
    from sqlalchemy import select
    from app.infrastructure.database.models import JournalItem, Voucher

    company_id = "COMP_PARTY_2"
    ledger_id, party_id, invoice_id = "LED_PARTY_2", "PTY_2", "INVD_1"
    batch = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_PARTY_2", "Beta Traders"),
        _create_party_event(company_id, party_id, ledger_id),
        _draft_invoice_event(company_id, invoice_id, party_id),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "b2"})
    assert resp.json()["processedCount"] == 3

    vouchers = (await db_session.execute(select(Voucher).where(Voucher.company_id == company_id))).scalars().all()
    journal_items = (await db_session.execute(select(JournalItem).where(JournalItem.company_id == company_id))).scalars().all()
    assert len(vouchers) == 0
    assert len(journal_items) == 0

    invoices = await client.get("/invoices", params={"companyId": company_id}, headers=auth_headers)
    body = invoices.json()
    assert len(body) == 1
    assert body[0]["status"] == "DRAFT"
    assert body[0]["voucherId"] is None


async def test_post_invoice_and_link_produces_posted_status(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_PARTY_3"
    ledger_id, party_id, invoice_id, voucher_id = "LED_PARTY_3", "PTY_3", "INVD_3", "V_3"
    setup_batch = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_PARTY_3", "Gamma Ltd"),
        _create_party_event(company_id, party_id, ledger_id),
        _draft_invoice_event(company_id, invoice_id, party_id),
    ])
    await client.post("/sync/outbox/batch", json=setup_batch, headers={**auth_headers, "Idempotency-Key": "b3a"})

    post_event, voucher_number = _post_sales_invoice_event(company_id, voucher_id, ledger_id, 100_000)
    post_batch = _batch(company_id, [post_event, _link_invoice_event(company_id, invoice_id, voucher_id)])
    resp = await client.post("/sync/outbox/batch", json=post_batch, headers={**auth_headers, "Idempotency-Key": "b3b"})
    assert resp.json()["processedCount"] == 2

    invoices = await client.get("/invoices", params={"companyId": company_id}, headers=auth_headers)
    inv = next(i for i in invoices.json() if i["invoiceId"] == invoice_id)
    assert inv["status"] == "POSTED"
    assert inv["voucherId"] == voucher_id
    # Phase 7B: invoiceNumber is assigned once at draft-creation and is never overwritten by
    # posting/linking - it must NOT equal the Voucher's own independently-generated number.
    assert inv["invoiceNumber"] == "SI-2026-0001"
    assert inv["invoiceNumber"] != voucher_number


async def test_partial_then_full_allocation_moves_status_to_paid(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_PARTY_4"
    ledger_id, party_id, invoice_id, voucher_id = "LED_PARTY_4", "PTY_4", "INVD_4", "V_4"
    setup_batch = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_PARTY_4", "Delta Inc"),
        _create_party_event(company_id, party_id, ledger_id),
        _draft_invoice_event(company_id, invoice_id, party_id),
    ])
    await client.post("/sync/outbox/batch", json=setup_batch, headers={**auth_headers, "Idempotency-Key": "b4a"})
    post_event, voucher_number = _post_sales_invoice_event(company_id, voucher_id, ledger_id, 100_000)
    await client.post(
        "/sync/outbox/batch",
        json=_batch(company_id, [post_event, _link_invoice_event(company_id, invoice_id, voucher_id)]),
        headers={**auth_headers, "Idempotency-Key": "b4b"},
    )

    partial_receipt = _receipt_settlement_event(company_id, "V_4_RCT1", ledger_id, voucher_id, 40_000)
    r1 = await client.post("/sync/outbox/batch", json=_batch(company_id, [partial_receipt]), headers={**auth_headers, "Idempotency-Key": "b4c"})
    assert r1.json()["processedCount"] == 1

    invoices = await client.get("/invoices", params={"companyId": company_id}, headers=auth_headers)
    inv = next(i for i in invoices.json() if i["invoiceId"] == invoice_id)
    assert inv["status"] == "PARTIALLY_PAID"

    final_receipt = _receipt_settlement_event(company_id, "V_4_RCT2", ledger_id, voucher_id, 60_000)
    r2 = await client.post("/sync/outbox/batch", json=_batch(company_id, [final_receipt]), headers={**auth_headers, "Idempotency-Key": "b4d"})
    assert r2.json()["processedCount"] == 1

    invoices = await client.get("/invoices", params={"companyId": company_id}, headers=auth_headers)
    inv = next(i for i in invoices.json() if i["invoiceId"] == invoice_id)
    assert inv["status"] == "PAID"


async def test_cancelled_voucher_makes_invoice_cancelled(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_PARTY_5"
    ledger_id, party_id, invoice_id, voucher_id = "LED_PARTY_5", "PTY_5", "INVD_5", "V_5"
    setup_batch = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_PARTY_5", "Epsilon LLC"),
        _create_party_event(company_id, party_id, ledger_id),
        _draft_invoice_event(company_id, invoice_id, party_id),
    ])
    await client.post("/sync/outbox/batch", json=setup_batch, headers={**auth_headers, "Idempotency-Key": "b5a"})
    post_event, voucher_number = _post_sales_invoice_event(company_id, voucher_id, ledger_id, 100_000)
    await client.post(
        "/sync/outbox/batch",
        json=_batch(company_id, [post_event, _link_invoice_event(company_id, invoice_id, voucher_id)]),
        headers={**auth_headers, "Idempotency-Key": "b5b"},
    )

    cancel_resp = await client.post(
        "/sync/outbox/batch", json=_batch(company_id, [_cancel_voucher_event(company_id, voucher_id, ledger_id, 100_000)]),
        headers={**auth_headers, "Idempotency-Key": "b5c"},
    )
    assert cancel_resp.json()["processedCount"] == 1

    invoices = await client.get("/invoices", params={"companyId": company_id}, headers=auth_headers)
    inv = next(i for i in invoices.json() if i["invoiceId"] == invoice_id)
    assert inv["status"] == "CANCELLED"


async def test_overdue_when_past_due_date_and_unpaid(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_PARTY_6"
    ledger_id, party_id, invoice_id, voucher_id = "LED_PARTY_6", "PTY_6", "INVD_6", "V_6"
    setup_batch = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_PARTY_6", "Zeta Co"),
        _create_party_event(company_id, party_id, ledger_id),
        _draft_invoice_event(company_id, invoice_id, party_id, due_date="2020-01-01"),
    ])
    await client.post("/sync/outbox/batch", json=setup_batch, headers={**auth_headers, "Idempotency-Key": "b6a"})
    post_event, voucher_number = _post_sales_invoice_event(company_id, voucher_id, ledger_id, 100_000)
    await client.post(
        "/sync/outbox/batch",
        json=_batch(company_id, [post_event, _link_invoice_event(company_id, invoice_id, voucher_id)]),
        headers={**auth_headers, "Idempotency-Key": "b6b"},
    )

    invoices = await client.get("/invoices", params={"companyId": company_id}, headers=auth_headers)
    inv = next(i for i in invoices.json() if i["invoiceId"] == invoice_id)
    assert inv["status"] == "OVERDUE"


async def test_cancel_draft_invoice_deletes_it(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_PARTY_7"
    ledger_id, party_id, invoice_id = "LED_PARTY_7", "PTY_7", "INVD_7"
    setup_batch = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_PARTY_7", "Eta Traders"),
        _create_party_event(company_id, party_id, ledger_id),
        _draft_invoice_event(company_id, invoice_id, party_id),
    ])
    await client.post("/sync/outbox/batch", json=setup_batch, headers={**auth_headers, "Idempotency-Key": "b7a"})

    resp = await client.post(
        "/sync/outbox/batch", json=_batch(company_id, [_cancel_draft_invoice_event(company_id, invoice_id)]),
        headers={**auth_headers, "Idempotency-Key": "b7b"},
    )
    assert resp.json()["processedCount"] == 1

    invoices = await client.get("/invoices", params={"companyId": company_id}, headers=auth_headers)
    assert all(i["invoiceId"] != invoice_id for i in invoices.json())


async def test_cannot_relink_already_posted_invoice(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_PARTY_8"
    ledger_id, party_id, invoice_id, voucher_id = "LED_PARTY_8", "PTY_8", "INVD_8", "V_8"
    setup_batch = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_PARTY_8", "Theta Corp"),
        _create_party_event(company_id, party_id, ledger_id),
        _draft_invoice_event(company_id, invoice_id, party_id),
    ])
    await client.post("/sync/outbox/batch", json=setup_batch, headers={**auth_headers, "Idempotency-Key": "b8a"})
    post_event, voucher_number = _post_sales_invoice_event(company_id, voucher_id, ledger_id, 100_000)
    await client.post(
        "/sync/outbox/batch",
        json=_batch(company_id, [post_event, _link_invoice_event(company_id, invoice_id, voucher_id)]),
        headers={**auth_headers, "Idempotency-Key": "b8b"},
    )

    resp = await client.post(
        "/sync/outbox/batch",
        json=_batch(company_id, [_link_invoice_event(company_id, invoice_id, "V_8_OTHER")]),
        headers={**auth_headers, "Idempotency-Key": "b8c"},
    )
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "INVOICE_ALREADY_POSTED"


async def test_get_parties_filtered_by_role(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_PARTY_9"
    batch = _batch(company_id, [
        _create_ledger_event(company_id, "LED_CUST_9", "GRP_DEBTORS_COMP_PARTY_9", "Customer Co"),
        _create_party_event(company_id, "PTY_CUST_9", "LED_CUST_9", role="CUSTOMER"),
        _create_ledger_event(company_id, "LED_SUPP_9", "GRP_CREDITORS_COMP_PARTY_9", "Supplier Co"),
        _create_party_event(company_id, "PTY_SUPP_9", "LED_SUPP_9", role="SUPPLIER"),
    ])
    await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "b9"})

    customers = await client.get("/parties", params={"companyId": company_id, "role": "CUSTOMER"}, headers=auth_headers)
    assert [p["partyId"] for p in customers.json()] == ["PTY_CUST_9"]

    suppliers = await client.get("/parties", params={"companyId": company_id, "role": "SUPPLIER"}, headers=auth_headers)
    assert [p["partyId"] for p in suppliers.json()] == ["PTY_SUPP_9"]

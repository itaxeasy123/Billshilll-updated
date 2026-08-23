"""Phase 7B (Document/Voucher Lifecycle Architecture) tests - TradeDocument creation/issue/convert/
cancel via the Outbox batch path, the raw POST route for non-Android callers, and the Phase 7A
invoice-numbering retrofit (invoiceNumber assigned once at draft-creation, never overwritten by
LINK_INVOICE_VOUCHER). Uses the same conftest fixtures (`client`, `auth_headers`, `db_session`) as
every other test file."""
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


def _create_party_event(company_id: str, party_id: str, ledger_id: str) -> dict:
    return _event(
        company_id, "CREATE_PARTY", "PARTY", party_id,
        party={
            "partyId": party_id, "ledgerId": ledger_id, "role": "CUSTOMER", "entityType": "BUSINESS",
            "displayName": "Test Party", "contactName": "", "creditLimitPaise": None,
            "paymentTermsType": "NET_30", "paymentTermsCustomDays": None, "isActive": True,
        },
    )


def _trade_document_line() -> dict:
    return {
        "lineId": uuid.uuid4().hex, "itemId": "ITEM_1", "itemName": "Widget", "hsnSacCode": "8471",
        "quantityRaw": 1000, "ratePaise": 50_000, "gstRatePercent": 18.0, "cessRatePercent": 0.0, "lineOrder": 1,
    }


def _create_trade_document_event(company_id: str, trade_document_id: str, document_type: str, document_number: str, party_id: str, source_trade_document_id: str | None = None) -> dict:
    return _event(
        company_id, "CREATE_TRADE_DOCUMENT", "TRADE_DOCUMENT", trade_document_id, financialYearId="FY_TEST",
        tradeDocument={
            "tradeDocumentId": trade_document_id, "documentType": document_type, "documentNumber": document_number,
            "partyId": party_id, "date": "2026-05-01", "status": "DRAFT", "sourceTradeDocumentId": source_trade_document_id,
            "narration": "", "lines": [_trade_document_line()],
        },
    )


def _issue_trade_document_event(company_id: str, trade_document_id: str) -> dict:
    return _event(company_id, "ISSUE_TRADE_DOCUMENT", "TRADE_DOCUMENT", trade_document_id, tradeDocument={"tradeDocumentId": trade_document_id})


def _convert_trade_document_event(company_id: str, trade_document_id: str) -> dict:
    return _event(company_id, "CONVERT_TRADE_DOCUMENT", "TRADE_DOCUMENT", trade_document_id, tradeDocument={"tradeDocumentId": trade_document_id})


def _cancel_trade_document_event(company_id: str, trade_document_id: str) -> dict:
    return _event(company_id, "CANCEL_TRADE_DOCUMENT", "TRADE_DOCUMENT", trade_document_id, tradeDocument={"tradeDocumentId": trade_document_id})


def _draft_invoice_event(company_id: str, invoice_id: str, party_id: str, invoice_number: str, source_trade_document_id: str | None = None) -> dict:
    return _event(
        company_id, "CREATE_DRAFT_INVOICE", "INVOICE", invoice_id, financialYearId="FY_TEST",
        invoice={
            "invoiceId": invoice_id, "invoiceType": "SALES_INVOICE", "invoiceNumber": invoice_number,
            "partyId": party_id, "date": "2026-05-01", "dueDate": None, "voucherId": None,
            "referenceInvoiceId": None, "sourceTradeDocumentId": source_trade_document_id, "narration": "",
            "lines": [{
                "lineId": uuid.uuid4().hex, "itemId": "ITEM_1", "itemName": "Widget", "hsnSacCode": "8471",
                "quantityRaw": 1000, "ratePaise": 50_000, "gstRatePercent": 18.0, "cessRatePercent": 0.0, "lineOrder": 1,
            }],
        },
    )


def _link_invoice_event(company_id: str, invoice_id: str, voucher_id: str) -> dict:
    return _event(company_id, "LINK_INVOICE_VOUCHER", "INVOICE", invoice_id, invoice={"invoiceId": invoice_id, "voucherId": voucher_id})


def _post_sales_invoice_event(company_id: str, voucher_id: str, party_ledger_id: str, amount_paise: int) -> dict:
    voucher_number = f"INV-{voucher_id}"
    return _event(
        company_id, "POST_SALES_INVOICE", "VOUCHER", voucher_id, financialYearId="FY_TEST",
        voucher={
            "voucherId": voucher_id, "voucherNumber": voucher_number, "voucherType": "SALES", "date": "2026-05-01",
            "referenceNumber": "", "narration": "", "totalAmountPaise": amount_paise, "isCancelled": False,
            "createdBy": "TESTER", "partyGstin": "", "isGstApplicable": False, "referenceVoucherId": None, "paymentMode": "",
        },
        journalLines=[
            {"itemId": uuid.uuid4().hex, "ledgerId": party_ledger_id, "ledgerName": "", "type": "DEBIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 1},
            {"itemId": uuid.uuid4().hex, "ledgerId": "LED_SALES_TEST", "ledgerName": "", "type": "CREDIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 2},
        ],
        stockLines=[], gstTransactions=[], settlements=[],
    ), voucher_number


async def test_draft_trade_document_has_zero_accounting_effect(client: AsyncClient, auth_headers: dict, db_session):
    from sqlalchemy import select
    from app.infrastructure.database.models import JournalItem, Voucher

    company_id = "COMP_TD_1"
    ledger_id, party_id, doc_id = "LED_TD_1", "PTY_TD_1", "TRD_1"
    batch = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_TD_1", "Customer A"),
        _create_party_event(company_id, party_id, ledger_id),
        _create_trade_document_event(company_id, doc_id, "QUOTATION", "EST-2026-0001", party_id),
    ])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": "b1"})
    assert resp.json()["processedCount"] == 3

    vouchers = (await db_session.execute(select(Voucher).where(Voucher.company_id == company_id))).scalars().all()
    journal_items = (await db_session.execute(select(JournalItem).where(JournalItem.company_id == company_id))).scalars().all()
    assert len(vouchers) == 0
    assert len(journal_items) == 0

    listing = await client.get("/trade-documents", params={"companyId": company_id}, headers=auth_headers)
    docs = listing.json()
    assert len(docs) == 1
    assert docs[0]["status"] == "DRAFT"
    assert docs[0]["documentNumber"] == "EST-2026-0001"


async def test_create_trade_document_rejects_posting_document_type(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_TD_2"
    ledger_id, party_id, doc_id = "LED_TD_2", "PTY_TD_2", "TRD_2"
    setup = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_TD_2", "Customer B"),
        _create_party_event(company_id, party_id, ledger_id),
    ])
    await client.post("/sync/outbox/batch", json=setup, headers={**auth_headers, "Idempotency-Key": "b2a"})

    resp = await client.post(
        "/sync/outbox/batch",
        json=_batch(company_id, [_create_trade_document_event(company_id, doc_id, "SALES_INVOICE", "SI-2026-0001", party_id)]),
        headers={**auth_headers, "Idempotency-Key": "b2b"},
    )
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "VALIDATION_ERROR"


async def test_issue_transitions_draft_to_issued_and_rejects_reissue(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_TD_3"
    ledger_id, party_id, doc_id = "LED_TD_3", "PTY_TD_3", "TRD_3"
    setup = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_TD_3", "Customer C"),
        _create_party_event(company_id, party_id, ledger_id),
        _create_trade_document_event(company_id, doc_id, "QUOTATION", "EST-2026-0001", party_id),
    ])
    await client.post("/sync/outbox/batch", json=setup, headers={**auth_headers, "Idempotency-Key": "b3a"})

    issue_resp = await client.post(
        "/sync/outbox/batch", json=_batch(company_id, [_issue_trade_document_event(company_id, doc_id)]),
        headers={**auth_headers, "Idempotency-Key": "b3b"},
    )
    assert issue_resp.json()["processedCount"] == 1

    listing = await client.get("/trade-documents", params={"companyId": company_id}, headers=auth_headers)
    assert listing.json()[0]["status"] == "ISSUED"

    reissue_resp = await client.post(
        "/sync/outbox/batch", json=_batch(company_id, [_issue_trade_document_event(company_id, doc_id)]),
        headers={**auth_headers, "Idempotency-Key": "b3c"},
    )
    assert reissue_resp.json()["processedCount"] == 0


async def test_convert_trade_document_to_trade_document_sets_source_and_marks_original_converted(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_TD_4"
    ledger_id, party_id = "LED_TD_4", "PTY_TD_4"
    quotation_id, sales_order_id = "TRD_4_QUO", "TRD_4_SO"
    setup = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_TD_4", "Customer D"),
        _create_party_event(company_id, party_id, ledger_id),
        _create_trade_document_event(company_id, quotation_id, "QUOTATION", "EST-2026-0001", party_id),
    ])
    await client.post("/sync/outbox/batch", json=setup, headers={**auth_headers, "Idempotency-Key": "b4a"})

    convert_batch = _batch(company_id, [
        _create_trade_document_event(company_id, sales_order_id, "SALES_ORDER", "SO-2026-0001", party_id, source_trade_document_id=quotation_id),
        _convert_trade_document_event(company_id, quotation_id),
    ])
    resp = await client.post("/sync/outbox/batch", json=convert_batch, headers={**auth_headers, "Idempotency-Key": "b4b"})
    assert resp.json()["processedCount"] == 2

    listing = (await client.get("/trade-documents", params={"companyId": company_id}, headers=auth_headers)).json()
    quotation = next(d for d in listing if d["tradeDocumentId"] == quotation_id)
    sales_order = next(d for d in listing if d["tradeDocumentId"] == sales_order_id)
    assert quotation["status"] == "CONVERTED"
    assert sales_order["sourceTradeDocumentId"] == quotation_id


async def test_convert_trade_document_rejects_already_converted_source(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_TD_5"
    ledger_id, party_id, doc_id = "LED_TD_5", "PTY_TD_5", "TRD_5"
    setup = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_TD_5", "Customer E"),
        _create_party_event(company_id, party_id, ledger_id),
        _create_trade_document_event(company_id, doc_id, "QUOTATION", "EST-2026-0001", party_id),
        _convert_trade_document_event(company_id, doc_id),
    ])
    await client.post("/sync/outbox/batch", json=setup, headers={**auth_headers, "Idempotency-Key": "b5a"})

    resp = await client.post(
        "/sync/outbox/batch", json=_batch(company_id, [_convert_trade_document_event(company_id, doc_id)]),
        headers={**auth_headers, "Idempotency-Key": "b5b"},
    )
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "TRADE_DOCUMENT_ALREADY_CONVERTED"


async def test_convert_trade_document_to_invoice_links_source_and_marks_original_converted(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_TD_6"
    ledger_id, party_id, doc_id, invoice_id = "LED_TD_6", "PTY_TD_6", "TRD_6", "INVD_6"
    setup = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_TD_6", "Customer F"),
        _create_party_event(company_id, party_id, ledger_id),
        _create_trade_document_event(company_id, doc_id, "SALES_ORDER", "SO-2026-0001", party_id),
    ])
    await client.post("/sync/outbox/batch", json=setup, headers={**auth_headers, "Idempotency-Key": "b6a"})

    convert_batch = _batch(company_id, [
        _draft_invoice_event(company_id, invoice_id, party_id, "SI-2026-0001", source_trade_document_id=doc_id),
        _convert_trade_document_event(company_id, doc_id),
    ])
    resp = await client.post("/sync/outbox/batch", json=convert_batch, headers={**auth_headers, "Idempotency-Key": "b6b"})
    assert resp.json()["processedCount"] == 2

    invoices = (await client.get("/invoices", params={"companyId": company_id}, headers=auth_headers)).json()
    invoice = next(i for i in invoices if i["invoiceId"] == invoice_id)
    assert invoice["voucherId"] is None
    assert invoice["status"] == "DRAFT"

    doc_listing = (await client.get("/trade-documents", params={"companyId": company_id}, headers=auth_headers)).json()
    assert doc_listing[0]["status"] == "CONVERTED"


async def test_invoice_number_assigned_at_draft_creation_never_overwritten_by_link(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_TD_7"
    ledger_id, party_id, invoice_id, voucher_id = "LED_TD_7", "PTY_TD_7", "INVD_7", "V_7"
    setup = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_TD_7", "Customer G"),
        _create_party_event(company_id, party_id, ledger_id),
        _draft_invoice_event(company_id, invoice_id, party_id, "SI-2026-0001"),
    ])
    await client.post("/sync/outbox/batch", json=setup, headers={**auth_headers, "Idempotency-Key": "b7a"})

    post_event, voucher_number = _post_sales_invoice_event(company_id, voucher_id, ledger_id, 100_000)
    assert voucher_number != "SI-2026-0001"
    await client.post(
        "/sync/outbox/batch",
        json=_batch(company_id, [post_event, _link_invoice_event(company_id, invoice_id, voucher_id)]),
        headers={**auth_headers, "Idempotency-Key": "b7b"},
    )

    invoices = (await client.get("/invoices", params={"companyId": company_id}, headers=auth_headers)).json()
    invoice = next(i for i in invoices if i["invoiceId"] == invoice_id)
    assert invoice["invoiceNumber"] == "SI-2026-0001"
    assert invoice["voucherId"] == voucher_id


async def test_cancel_trade_document_draft_hard_deleted_issued_preserved_converted_rejected(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_TD_8"
    ledger_id, party_id = "LED_TD_8", "PTY_TD_8"
    draft_id, issued_id, converted_id = "TRD_8_DRAFT", "TRD_8_ISSUED", "TRD_8_CONVERTED"
    setup = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_TD_8", "Customer H"),
        _create_party_event(company_id, party_id, ledger_id),
        _create_trade_document_event(company_id, draft_id, "QUOTATION", "EST-2026-0001", party_id),
        _create_trade_document_event(company_id, issued_id, "QUOTATION", "EST-2026-0002", party_id),
        _issue_trade_document_event(company_id, issued_id),
        _create_trade_document_event(company_id, converted_id, "QUOTATION", "EST-2026-0003", party_id),
        _convert_trade_document_event(company_id, converted_id),
    ])
    await client.post("/sync/outbox/batch", json=setup, headers={**auth_headers, "Idempotency-Key": "b8a"})

    cancel_batch = _batch(company_id, [
        _cancel_trade_document_event(company_id, draft_id),
        _cancel_trade_document_event(company_id, issued_id),
        _cancel_trade_document_event(company_id, converted_id),
    ])
    resp = await client.post("/sync/outbox/batch", json=cancel_batch, headers={**auth_headers, "Idempotency-Key": "b8b"})
    body = resp.json()
    assert body["processedCount"] == 2  # draft + issued succeed
    assert len(body["rejections"]) == 1
    assert body["rejections"][0]["reason"].startswith("Document") and "converted" in body["rejections"][0]["reason"]

    listing = (await client.get("/trade-documents", params={"companyId": company_id}, headers=auth_headers)).json()
    ids = {d["tradeDocumentId"]: d for d in listing}
    assert draft_id not in ids
    assert ids[issued_id]["status"] == "CANCELLED"
    assert ids[converted_id]["status"] == "CONVERTED"


async def test_post_quotation_route_creates_draft_with_server_generated_number_and_is_idempotent(client: AsyncClient, auth_headers: dict):
    company_id = "COMP_TD_9"
    ledger_id, party_id = "LED_TD_9", "PTY_TD_9"
    setup = _batch(company_id, [
        _create_ledger_event(company_id, ledger_id, "GRP_DEBTORS_COMP_TD_9", "Customer I"),
        _create_party_event(company_id, party_id, ledger_id),
    ])
    await client.post("/sync/outbox/batch", json=setup, headers={**auth_headers, "Idempotency-Key": "b9a"})

    body = {
        "financialYearId": "FY_TEST", "partyId": party_id, "date": "2026-05-01", "narration": "",
        "lines": [_trade_document_line()],
    }
    first = await client.post("/quotations", params={"companyId": company_id}, json=body, headers={**auth_headers, "Idempotency-Key": "post-key-1"})
    assert first.status_code == 200
    first_body = first.json()
    assert first_body["documentNumber"] == "EST-2026-0001"

    second = await client.post("/quotations", params={"companyId": company_id}, json=body, headers={**auth_headers, "Idempotency-Key": "post-key-1"})
    assert second.json() == first_body

    listing = (await client.get("/trade-documents", params={"companyId": company_id}, headers=auth_headers)).json()
    assert len(listing) == 1

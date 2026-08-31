"""GST-Only Sync Path server tests. Proves the full SyncEvent -> dispatcher -> gst_transaction_
commands -> database path for POST_GST_TRANSACTION: persisted with voucher_id NULL, zero
accounting effect (no Voucher/JournalItem/Ledger-balance change), idempotent replay, and rejection
of malformed/cross-company/unknown events via the existing structured error contract - never a
second, parallel API.
"""
from __future__ import annotations

import json
import uuid

import pytest
from httpx import AsyncClient
from sqlalchemy import select

from tests.helpers import make_batch_request, make_sync_event

pytestmark = pytest.mark.asyncio


def make_gst_only_sync_event(
    company_id: str,
    financial_year_id: str = "FY_TEST",
    party_ledger_id: str = "LED_CUSTOMER",
    taxable_amount_paise: int = 100_000,
    cgst_paise: int = 9_000,
    sgst_paise: int = 9_000,
    igst_paise: int = 0,
    direction: str = "OUTPUT",
    voucher_type: str | None = "SALES",
    gst_transaction_id: str | None = None,
    # D1b (GST-Only Purchase + Sales/Purchase Return + GST Fact Hardening) additions - all optional/
    # defaulted so every pre-existing call site above keeps behaving identically.
    charge_type: str = "FORWARD_CHARGE",
    supply_nature: str = "NORMAL",
    transaction_group_id: str | None = None,
    transaction_date: str | None = "2026-05-10",
    party_gst_registration_status: str | None = "REGISTERED",
) -> dict:
    gst_transaction_id = gst_transaction_id or f"GST_{uuid.uuid4().hex[:8]}"
    return {
        "schemaVersion": 1,
        "eventId": uuid.uuid4().hex,
        "idempotencyKey": uuid.uuid4().hex,
        "companyId": company_id,
        "financialYearId": financial_year_id,
        "operation": "POST_GST_TRANSACTION",
        "aggregateType": "GST_TRANSACTION",
        "aggregateId": gst_transaction_id,
        "voucher": None,
        "journalLines": [],
        "stockLines": [],
        "gstTransactions": [
            {
                "gstTransactionId": gst_transaction_id,
                "voucherType": voucher_type,
                "partyLedgerId": party_ledger_id,
                "partyGstin": "27AAAAA0000A1Z5",
                "placeOfSupply": "27",
                "supplyType": "INTRA_STATE",
                "itemId": "ITEM_X",
                "hsnSacCode": "9983",
                "quantityRaw": 100,
                "taxableAmountPaise": taxable_amount_paise,
                "gstRatePercent": 18.0,
                "cgstPaise": cgst_paise,
                "sgstPaise": sgst_paise,
                "igstPaise": igst_paise,
                "cessPaise": 0,
                "direction": direction,
                "lineOrder": 1,
                "chargeType": charge_type,
                "supplyNature": supply_nature,
                "transactionGroupId": transaction_group_id,
                "transactionDate": transaction_date,
                "partyGstRegistrationStatus": party_gst_registration_status,
            }
        ],
        "settlements": [],
    }


def make_gst_only_batch(company_id: str, events: list[dict]) -> dict:
    return {
        "companyId": company_id,
        "deviceId": "DEV_TEST",
        "items": [
            {
                "syncId": uuid.uuid4().hex,
                "entityType": "GST_TRANSACTION",
                "entityId": e["aggregateId"],
                "operation": e["operation"],
                "payloadJson": json.dumps(e),
                "idempotencyKey": e["idempotencyKey"],
                "version": 1,
                "clientTimestamp": 0,
            }
            for e in events
        ],
    }


async def _seed_financial_year(db_session, company_id: str, fy_id: str = "FY_TEST") -> None:
    from app.infrastructure.database.models import FinancialYear

    db_session.add(
        FinancialYear(
            financial_year_id=fy_id, company_id=company_id, fy_code="FY 2026-27",
            start_date="2026-04-01", end_date="2027-03-31", is_current=True, is_locked=False,
        )
    )
    await db_session.commit()


async def test_valid_gst_only_transaction_accepted_and_persisted(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import GstTransaction

    await _seed_financial_year(db_session, "COMP_GST1")
    event = make_gst_only_sync_event("COMP_GST1", gst_transaction_id="GST_ONE")
    batch = make_gst_only_batch("COMP_GST1", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["success"] is True
    assert body["processedCount"] == 1
    assert body["rejections"] == []

    result = await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_ONE"))
    stored = result.scalar_one_or_none()
    assert stored is not None
    assert stored.voucher_id is None
    assert stored.voucher_type == "SALES"
    assert stored.company_id == "COMP_GST1"
    assert stored.cgst_paise == 9_000
    assert stored.sgst_paise == 9_000
    assert stored.taxable_amount_paise == 100_000


async def test_gst_only_event_missing_voucher_type_rejected(client: AsyncClient, auth_headers: dict, db_session):
    """Transaction/Contract Hardening: a missing explicit classification must be rejected, never
    silently defaulted or derived from direction."""
    await _seed_financial_year(db_session, "COMP_GST_NOTYPE")
    event = make_gst_only_sync_event("COMP_GST_NOTYPE", voucher_type=None, gst_transaction_id="GST_NO_TYPE")
    batch = make_gst_only_batch("COMP_GST_NOTYPE", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 0
    assert "voucherType" in body["rejections"][0]["reason"]


async def test_gst_only_event_with_non_gst_relevant_voucher_type_rejected(client: AsyncClient, auth_headers: dict, db_session):
    """RECEIPT/PAYMENT/CONTRA/JOURNAL are real VoucherType values but are never GST document
    types - the phase spec explicitly forbids treating them as GST-relevant merely because they
    are accounting VoucherTypes."""
    await _seed_financial_year(db_session, "COMP_GST_BADTYPE")
    event = make_gst_only_sync_event("COMP_GST_BADTYPE", voucher_type="RECEIPT", gst_transaction_id="GST_BAD_TYPE")
    batch = make_gst_only_batch("COMP_GST_BADTYPE", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 0
    assert "not a GST-relevant transaction type" in body["rejections"][0]["reason"]


async def test_gst_only_event_does_not_infer_classification_from_direction(client: AsyncClient, auth_headers: dict, db_session):
    """The exact regression this phase closes: OUTPUT direction alone must never be silently
    treated as SALES. Sending OUTPUT direction with no voucherType must still be rejected."""
    await _seed_financial_year(db_session, "COMP_GST_NOINFER")
    event = make_gst_only_sync_event("COMP_GST_NOINFER", direction="OUTPUT", voucher_type=None, gst_transaction_id="GST_NO_INFER")
    batch = make_gst_only_batch("COMP_GST_NOINFER", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 0

    from app.infrastructure.database.models import GstTransaction
    result = await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_NO_INFER"))
    assert result.scalar_one_or_none() is None


async def test_gst_only_transaction_creates_no_voucher_no_journal_item_no_ledger_change(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import JournalItem, Ledger, Voucher

    await _seed_financial_year(db_session, "COMP_GST2")
    db_session.add(Ledger(ledger_id="LED_CUSTOMER", company_id="COMP_GST2", group_id="GRP_DEBTORS_COMP_GST2", name="Test Customer", current_balance_paise=0, current_balance_type="DEBIT"))
    await db_session.commit()

    event = make_gst_only_sync_event("COMP_GST2", gst_transaction_id="GST_TWO")
    batch = make_gst_only_batch("COMP_GST2", [event])
    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    assert resp.json()["processedCount"] == 1

    vouchers = (await db_session.execute(select(Voucher).where(Voucher.company_id == "COMP_GST2"))).scalars().all()
    journal_items = (await db_session.execute(select(JournalItem).where(JournalItem.company_id == "COMP_GST2"))).scalars().all()
    ledger = (await db_session.execute(select(Ledger).where(Ledger.ledger_id == "LED_CUSTOMER"))).scalar_one()
    assert vouchers == []
    assert journal_items == []
    assert ledger.current_balance_paise == 0
    assert ledger.current_balance_type == "DEBIT"


async def test_duplicate_gst_only_event_is_idempotent(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import GstTransaction

    await _seed_financial_year(db_session, "COMP_GST3")
    event = make_gst_only_sync_event("COMP_GST3", gst_transaction_id="GST_DUP")
    batch = make_gst_only_batch("COMP_GST3", [event])

    # Same idempotencyKey on the underlying event (not just the HTTP header) - a genuine outbox
    # replay, e.g. a retried batch after a dropped response.
    idem_header = uuid.uuid4().hex
    resp1 = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": idem_header})
    resp2 = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})

    assert resp1.json()["processedCount"] == 1
    assert resp2.json()["processedCount"] == 1
    assert resp2.json()["rejections"] == []

    result = await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_DUP"))
    stored = result.scalars().all()
    assert len(stored) == 1


async def test_gst_only_event_with_mismatched_batch_company_rejected(client: AsyncClient, auth_headers: dict, db_session):
    await _seed_financial_year(db_session, "COMP_GST4")
    # Event body claims a different company than the batch envelope - the existing, generic
    # tenant check in sync.py must catch this for every operation, GST-only included.
    event = make_gst_only_sync_event("COMP_GST4_OTHER", gst_transaction_id="GST_MISMATCH")
    batch = make_gst_only_batch("COMP_GST4", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "TENANT_MISMATCH"


async def test_gst_only_event_with_financial_year_from_another_company_rejected(client: AsyncClient, auth_headers: dict, db_session):
    await _seed_financial_year(db_session, "COMP_GST5A", fy_id="FY_OWNED_BY_A")
    # Company B references Company A's real financial-year id - must be rejected, never silently
    # posted against the wrong company's books.
    event = make_gst_only_sync_event("COMP_GST5B", financial_year_id="FY_OWNED_BY_A", gst_transaction_id="GST_CROSS_FY")
    batch = make_gst_only_batch("COMP_GST5B", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "INVALID_FINANCIAL_YEAR"


async def test_gst_only_event_with_nonexistent_financial_year_rejected(client: AsyncClient, auth_headers: dict, db_session):
    event = make_gst_only_sync_event("COMP_GST6", financial_year_id="FY_DOES_NOT_EXIST", gst_transaction_id="GST_NO_FY")
    batch = make_gst_only_batch("COMP_GST6", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "INVALID_FINANCIAL_YEAR"


async def test_unknown_gst_operation_rejected_via_existing_error_contract(client: AsyncClient, auth_headers: dict, db_session):
    await _seed_financial_year(db_session, "COMP_GST7")
    event = make_gst_only_sync_event("COMP_GST7", gst_transaction_id="GST_UNKNOWN_OP")
    event["operation"] = "POST_GST_TRANSACTION_TYPO"
    batch = make_gst_only_batch("COMP_GST7", [event])
    # Envelope-level operation must match the event's own for the dispatcher's set-membership
    # check, which reads event.operation (decoded from payloadJson), not the envelope field.
    batch["items"][0]["operation"] = "POST_GST_TRANSACTION_TYPO"

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 0
    assert "Unknown sync operation" in body["rejections"][0]["reason"]


async def test_existing_voucher_sync_still_works_alongside_gst_only_operation(client: AsyncClient, auth_headers: dict, db_session):
    """Not a regression test in isolation (the full suite already proves this) - a direct,
    same-file demonstration that adding POST_GST_TRANSACTION did not disturb the pre-existing
    voucher dispatch branch it sits next to in outbox_dispatcher.py."""
    await _seed_financial_year(db_session, "COMP_GST8")
    voucher_event = make_sync_event("COMP_GST8", voucher_number="JRN-GST8")
    batch = make_batch_request("COMP_GST8", [voucher_event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["success"] is True
    assert body["processedCount"] == 1


# ==========================================================================
# D1b (GST-Only Purchase + Sales/Purchase Return + GST Fact Hardening)
# ==========================================================================

async def test_gst_only_purchase_accepted_and_persisted_with_no_accounting_effect(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import GstTransaction, JournalItem, Voucher

    await _seed_financial_year(db_session, "COMP_D1B_PUR")
    event = make_gst_only_sync_event(
        "COMP_D1B_PUR", party_ledger_id="LED_SUPPLIER", direction="INPUT",
        voucher_type="PURCHASE", gst_transaction_id="GST_PUR_ONE",
    )
    batch = make_gst_only_batch("COMP_D1B_PUR", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["success"] is True
    assert body["processedCount"] == 1

    stored = (await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_PUR_ONE"))).scalar_one()
    assert stored.voucher_id is None
    assert stored.voucher_type == "PURCHASE"
    assert stored.direction == "INPUT"

    vouchers = (await db_session.execute(select(Voucher).where(Voucher.company_id == "COMP_D1B_PUR"))).scalars().all()
    journal_items = (await db_session.execute(select(JournalItem).where(JournalItem.company_id == "COMP_D1B_PUR"))).scalars().all()
    assert vouchers == []
    assert journal_items == []


async def test_gst_only_credit_note_and_debit_note_voucher_types_accepted(client: AsyncClient, auth_headers: dict, db_session):
    """The server's own _GST_RELEVANT_VOUCHER_TYPES allowlist already included CREDIT_NOTE/
    DEBIT_NOTE before D1b (confirmed during the D1b audit) - this proves it end-to-end through the
    real sync route for both, not just by reading the allowlist source."""
    from app.infrastructure.database.models import GstTransaction

    await _seed_financial_year(db_session, "COMP_D1B_NOTES")
    credit_event = make_gst_only_sync_event(
        "COMP_D1B_NOTES", voucher_type="CREDIT_NOTE", direction="OUTPUT", gst_transaction_id="GST_CN_ONE",
        cgst_paise=-9_000, sgst_paise=-9_000, taxable_amount_paise=-100_000,
    )
    debit_event = make_gst_only_sync_event(
        "COMP_D1B_NOTES", voucher_type="DEBIT_NOTE", direction="INPUT", gst_transaction_id="GST_DN_ONE",
        cgst_paise=-9_000, sgst_paise=-9_000, taxable_amount_paise=-100_000,
    )
    batch = make_gst_only_batch("COMP_D1B_NOTES", [credit_event, debit_event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 2
    assert body["rejections"] == []

    cn = (await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_CN_ONE"))).scalar_one()
    dn = (await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_DN_ONE"))).scalar_one()
    assert cn.voucher_type == "CREDIT_NOTE" and cn.taxable_amount_paise == -100_000
    assert dn.voucher_type == "DEBIT_NOTE" and dn.taxable_amount_paise == -100_000


async def test_gst_only_charge_type_rcm_and_forward_charge_remain_distinguishable(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import GstTransaction

    await _seed_financial_year(db_session, "COMP_D1B_RCM")
    forward = make_gst_only_sync_event(
        "COMP_D1B_RCM", voucher_type="PURCHASE", direction="INPUT", gst_transaction_id="GST_FWD",
        charge_type="FORWARD_CHARGE",
    )
    reverse = make_gst_only_sync_event(
        "COMP_D1B_RCM", voucher_type="PURCHASE", direction="INPUT", gst_transaction_id="GST_RCM",
        charge_type="REVERSE_CHARGE",
    )
    batch = make_gst_only_batch("COMP_D1B_RCM", [forward, reverse])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    assert resp.json()["processedCount"] == 2

    fwd_row = (await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_FWD"))).scalar_one()
    rcm_row = (await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_RCM"))).scalar_one()
    assert fwd_row.charge_type == "FORWARD_CHARGE"
    assert rcm_row.charge_type == "REVERSE_CHARGE"


async def test_gst_only_pre_d1b_event_missing_new_fields_still_accepted_with_safe_defaults(client: AsyncClient, auth_headers: dict, db_session):
    """Backward compatibility: an already-queued event serialized by a pre-D1b Android build has no
    chargeType/supplyNature/transactionGroupId/transactionDate/partyGstRegistrationStatus keys at
    all - must never hard-fail parsing, and must fall back to safe, disclosed defaults."""
    from app.infrastructure.database.models import GstTransaction

    await _seed_financial_year(db_session, "COMP_D1B_OLDEVT")
    event = make_gst_only_sync_event("COMP_D1B_OLDEVT", gst_transaction_id="GST_OLD_EVT")
    # Strip the D1b-only keys entirely, simulating the exact JSON shape a pre-D1b client sent.
    line = event["gstTransactions"][0]
    for key in ("chargeType", "supplyNature", "transactionGroupId", "transactionDate", "partyGstRegistrationStatus"):
        del line[key]
    batch = make_gst_only_batch("COMP_D1B_OLDEVT", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 1
    assert body["rejections"] == []

    stored = (await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_OLD_EVT"))).scalar_one()
    assert stored.charge_type == "FORWARD_CHARGE"
    assert stored.supply_nature == "NORMAL"
    # No transactionGroupId supplied -> falls back to this line's own gstTransactionId.
    assert stored.transaction_group_id == "GST_OLD_EVT"
    assert stored.transaction_date is None
    assert stored.party_gst_registration_status is None


async def test_gst_only_explicit_transaction_group_id_preserved(client: AsyncClient, auth_headers: dict, db_session):
    """Multi-line correlation: two lines of the SAME business transaction share one explicit
    transactionGroupId, which the server must persist verbatim (not fall back for a present value)."""
    from app.infrastructure.database.models import GstTransaction

    await _seed_financial_year(db_session, "COMP_D1B_GROUP")
    group_id = f"GROUP_{uuid.uuid4().hex[:8]}"
    event = make_gst_only_sync_event("COMP_D1B_GROUP", gst_transaction_id="GST_G1", transaction_group_id=group_id)
    # Add a second line sharing the same group id, same event (one business transaction, two lines).
    second_line = dict(event["gstTransactions"][0])
    second_line["gstTransactionId"] = "GST_G2"
    second_line["lineOrder"] = 2
    event["gstTransactions"].append(second_line)
    batch = make_gst_only_batch("COMP_D1B_GROUP", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    assert resp.json()["processedCount"] == 1

    rows = (await db_session.execute(select(GstTransaction).where(GstTransaction.transaction_group_id == group_id))).scalars().all()
    assert len(rows) == 2
    assert {r.gst_transaction_id for r in rows} == {"GST_G1", "GST_G2"}


async def test_gst_only_transaction_date_and_registration_status_persist(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import GstTransaction

    await _seed_financial_year(db_session, "COMP_D1B_DATE")
    event = make_gst_only_sync_event(
        "COMP_D1B_DATE", gst_transaction_id="GST_DATE_ONE",
        transaction_date="2026-06-15", party_gst_registration_status="UNREGISTERED",
    )
    batch = make_gst_only_batch("COMP_D1B_DATE", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    assert resp.json()["processedCount"] == 1

    stored = (await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_DATE_ONE"))).scalar_one()
    assert stored.transaction_date == "2026-06-15"
    assert stored.party_gst_registration_status == "UNREGISTERED"


async def test_gst_only_purchase_duplicate_event_is_idempotent(client: AsyncClient, auth_headers: dict, db_session):
    from app.infrastructure.database.models import GstTransaction

    await _seed_financial_year(db_session, "COMP_D1B_PUR_DUP")
    event = make_gst_only_sync_event("COMP_D1B_PUR_DUP", voucher_type="PURCHASE", direction="INPUT", gst_transaction_id="GST_PUR_DUP")
    batch = make_gst_only_batch("COMP_D1B_PUR_DUP", [event])

    idem_header = uuid.uuid4().hex
    resp1 = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": idem_header})
    resp2 = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    assert resp1.json()["processedCount"] == 1
    assert resp2.json()["processedCount"] == 1

    stored = (await db_session.execute(select(GstTransaction).where(GstTransaction.gst_transaction_id == "GST_PUR_DUP"))).scalars().all()
    assert len(stored) == 1


async def test_gst_only_purchase_with_mismatched_batch_company_rejected(client: AsyncClient, auth_headers: dict, db_session):
    await _seed_financial_year(db_session, "COMP_D1B_PUR_TENANT")
    event = make_gst_only_sync_event(
        "COMP_D1B_PUR_TENANT_OTHER", voucher_type="PURCHASE", direction="INPUT", gst_transaction_id="GST_PUR_TENANT_MISMATCH",
    )
    batch = make_gst_only_batch("COMP_D1B_PUR_TENANT", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "TENANT_MISMATCH"


async def test_gst_only_purchase_with_financial_year_from_another_company_rejected(client: AsyncClient, auth_headers: dict, db_session):
    await _seed_financial_year(db_session, "COMP_D1B_PUR_FY_A", fy_id="FY_D1B_OWNED_BY_A")
    event = make_gst_only_sync_event(
        "COMP_D1B_PUR_FY_B", financial_year_id="FY_D1B_OWNED_BY_A", voucher_type="PURCHASE",
        direction="INPUT", gst_transaction_id="GST_PUR_CROSS_FY",
    )
    batch = make_gst_only_batch("COMP_D1B_PUR_FY_B", [event])

    resp = await client.post("/sync/outbox/batch", json=batch, headers={**auth_headers, "Idempotency-Key": uuid.uuid4().hex})
    body = resp.json()
    assert body["processedCount"] == 0
    assert body["rejections"][0]["conflictCode"] == "INVALID_FINANCIAL_YEAR"

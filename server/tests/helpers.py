"""Shared test payload builders."""
from __future__ import annotations

import json
import time
import uuid


def make_sync_event(
    company_id: str,
    financial_year_id: str = "FY_TEST",
    operation: str = "POST_JOURNAL",
    debit_ledger: str = "LED_BANK",
    credit_ledger: str = "LED_CAPITAL",
    amount_paise: int = 100_000,
    voucher_id: str | None = None,
    voucher_number: str | None = None,
) -> dict:
    voucher_id = voucher_id or f"V_{uuid.uuid4().hex[:8]}"
    voucher_number = voucher_number or f"JRN-{uuid.uuid4().hex[:6]}"
    return {
        "schemaVersion": 1,
        "eventId": uuid.uuid4().hex,
        "idempotencyKey": uuid.uuid4().hex,
        "companyId": company_id,
        "financialYearId": financial_year_id,
        "operation": operation,
        "aggregateType": "VOUCHER",
        "aggregateId": voucher_id,
        "voucher": {
            "voucherId": voucher_id,
            "voucherNumber": voucher_number,
            "voucherType": "JOURNAL",
            "date": "2026-05-10",
            "referenceNumber": "",
            "narration": "Test journal",
            "totalAmountPaise": amount_paise,
            "isCancelled": False,
            "createdBy": "TESTER",
            "partyGstin": "",
            "isGstApplicable": False,
            "referenceVoucherId": None,
            "paymentMode": "",
        },
        "journalLines": [
            {"itemId": uuid.uuid4().hex, "ledgerId": debit_ledger, "ledgerName": "", "type": "DEBIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 1},
            {"itemId": uuid.uuid4().hex, "ledgerId": credit_ledger, "ledgerName": "", "type": "CREDIT", "amountPaise": amount_paise, "narration": "", "lineOrder": 2},
        ],
        "stockLines": [],
        "gstTransactions": [],
        "settlements": [],
    }


def make_batch_request(company_id: str, events: list[dict], device_id: str = "DEV_TEST") -> dict:
    return {
        "companyId": company_id,
        "deviceId": device_id,
        "items": [
            {
                "syncId": uuid.uuid4().hex,
                "entityType": "VOUCHER",
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

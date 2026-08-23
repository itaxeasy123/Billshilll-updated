"""
A generic, DTO-agnostic CSV engine (Phase 7E, Section 8/9) - built on Python's standard `csv`
module (RFC 4180 quoting/escaping is handled by the stdlib, never hand-rolled string
concatenation). Every monetary value passed in must already be a paise integer (as a string) -
this module never converts anything to a float; that discipline is enforced by the caller
(`export_service.py`'s dict shapes only ever carry `...Paise` integer fields).
"""
from __future__ import annotations

import csv
import io


def write_csv(headers: list[str], rows: list[list]) -> str:
    """[rows] must all have the same length as [headers]. Row/column order is exactly the
    caller's own list order - never re-sorted. Uses `\r\n` line endings per RFC 4180."""
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\r\n")
    writer.writerow(headers)
    for row in rows:
        writer.writerow(["" if v is None else v for v in row])
    return buffer.getvalue()


def voucher_csv(voucher: dict) -> str:
    headers = [
        "voucherId", "voucherNumber", "voucherType", "date", "referenceNumber", "narration",
        "totalAmountPaise", "isPosted", "isCancelled", "ledgerId", "ledgerName", "drCr", "lineAmountPaise", "lineNarration", "lineOrder",
    ]
    rows = [
        [
            voucher["voucherId"], voucher["voucherNumber"], voucher["voucherType"], voucher["date"],
            voucher["referenceNumber"], voucher["narration"], voucher["totalAmountPaise"], voucher["isPosted"], voucher["isCancelled"],
            line["ledgerId"], line["ledgerName"], line["type"], line["amountPaise"], line["narration"], line["lineOrder"],
        ]
        for line in voucher["journalLines"]
    ]
    return write_csv(headers, rows)


def parties_csv(parties: list[dict]) -> str:
    headers = ["partyId", "ledgerId", "role", "entityType", "displayName", "contactName", "creditLimitPaise", "paymentTerms", "isActive"]
    rows = [[p["partyId"], p["ledgerId"], p["role"], p["entityType"], p["displayName"], p["contactName"], p["creditLimitPaise"], p["paymentTerms"], p["isActive"]] for p in parties]
    return write_csv(headers, rows)


def ledgers_csv(ledgers: list[dict]) -> str:
    headers = ["ledgerId", "groupId", "name", "code", "openingBalancePaise", "openingBalanceType", "currentBalancePaise", "currentBalanceType", "gstin", "pan", "stateCode", "address", "isSystem", "isActive"]
    rows = [
        [l["ledgerId"], l["groupId"], l["name"], l["code"], l["openingBalancePaise"], l["openingBalanceType"], l["currentBalancePaise"], l["currentBalanceType"], l["gstin"], l["pan"], l["stateCode"], l["address"], l["isSystem"], l["isActive"]]
        for l in ledgers
    ]
    return write_csv(headers, rows)


def trial_balance_csv(dto: dict) -> str:
    headers = ["ledgerId", "ledgerName", "groupId", "groupName", "primaryGroup", "openingDebitPaise", "openingCreditPaise", "transactionDebitPaise", "transactionCreditPaise", "closingDebitPaise", "closingCreditPaise"]
    rows = [
        [r["ledgerId"], r["ledgerName"], r["groupId"], r["groupName"], r["primaryGroup"], r["openingDebitPaise"], r["openingCreditPaise"], r["transactionDebitPaise"], r["transactionCreditPaise"], r["closingDebitPaise"], r["closingCreditPaise"]]
        for r in dto["rows"]
    ]
    return write_csv(headers, rows)


def outstanding_csv(dto: dict) -> str:
    headers = ["invoiceId", "invoiceNumber", "invoiceType", "partyId", "partyName", "voucherNumber", "date", "dueDate", "totalAmountPaise", "outstandingAmountPaise", "status", "daysOutstanding", "agingBucket"]
    rows = [
        [r["invoiceId"], r["invoiceNumber"], r["invoiceType"], r["partyId"], r["partyName"], r["voucherNumber"], r["date"], r["dueDate"], r["totalAmountPaise"], r["outstandingAmountPaise"], r["status"], r["daysOutstanding"], r["agingBucket"]]
        for r in dto["rows"]
    ]
    return write_csv(headers, rows)


def gst_summary_csv(dto: dict) -> str:
    headers = ["companyName", "gstin", "period", "totalTaxableOutwardPaise", "totalTaxOutwardPaise", "totalTaxableInwardPaise", "totalTaxInwardItcPaise", "netTaxPayablePaise", "totalCessPaise", "netCessPayablePaise"]
    row = [dto["companyName"], dto["gstin"], dto["period"], dto["totalTaxableOutwardPaise"], dto["totalTaxOutwardPaise"], dto["totalTaxableInwardPaise"], dto["totalTaxInwardItcPaise"], dto["netTaxPayablePaise"], dto["totalCessPaise"], dto["netCessPayablePaise"]]
    return write_csv(headers, [row])


def gst_transactions_csv(transactions: list[dict]) -> str:
    headers = ["gstTransactionId", "voucherId", "voucherType", "partyGstin", "placeOfSupply", "supplyType", "hsnSacCode", "isService", "taxableAmountPaise", "gstRatePercent", "cgstPaise", "sgstPaise", "igstPaise", "cessPaise", "direction", "lineOrder"]
    rows = [
        [t["gstTransactionId"], t["voucherId"], t["voucherType"], t["partyGstin"], t["placeOfSupply"], t["supplyType"], t["hsnSacCode"], t["isService"], t["taxableAmountPaise"], t["gstRatePercent"], t["cgstPaise"], t["sgstPaise"], t["igstPaise"], t["cessPaise"], t["direction"], t["lineOrder"]]
        for t in transactions
    ]
    return write_csv(headers, rows)

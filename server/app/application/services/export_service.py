"""
Phase 7E - Export Architecture & Data Interchange (application service layer).

Every function here is READ -> MAP -> SERIALIZE (Section 23): it reads already-authoritative data
via an existing query/service function (`application/queries/reports.py`,
`application/services/document_service.py`, or a direct company-scoped read of a single row), maps
it into a plain, JSON-serializable "export DTO" dict shaped to match Android's `domain.export.*`
Kotlin data classes field-for-field (Section 25: cross-platform consistency), and returns it.
NOTHING here performs accounting/GST/report calculation - report figures come straight from
`application/queries/reports.py`; GST figures come straight from the already-persisted
`GstTransaction` rows (never `ledgerName.contains(...)` string-guessing).

`GET /exports/.../pdf` does not exist in this phase - PDF is out of scope for 7E entirely (that's
7D's Android-only concern); 7E is JSON/CSV/GSTR-JSON only.
"""
from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.application.queries import reports as report_queries
from app.application.services import document_service
from app.domain.errors import ExportFormatUnsupported, ResourceNotFound
from app.infrastructure.database.models import Group, Ledger, Party, Voucher, JournalItem, GstTransaction

_TABULAR_EXPORT_TYPES = {"VOUCHER", "PARTY", "LEDGER", "TRIAL_BALANCE", "OUTSTANDING", "GST_SUMMARY", "GST_TRANSACTIONS"}


def supports_format(export_type: str, format_: str) -> bool:
    if format_ == "json":
        return True
    if format_ == "csv":
        return export_type in _TABULAR_EXPORT_TYPES
    if format_ == "gstr-json":
        return export_type == "GST_TRANSACTIONS"
    return False


def require_supported_format(export_type: str, format_: str) -> None:
    if not supports_format(export_type, format_):
        raise ExportFormatUnsupported(f"Format '{format_}' is not supported for export type '{export_type}'.")


# ==================== Voucher / Party / Ledger export ====================

async def export_voucher(db: AsyncSession, company_id: str, voucher_id: str) -> dict:
    voucher = (await db.execute(select(Voucher).where(Voucher.company_id == company_id, Voucher.voucher_id == voucher_id))).scalar_one_or_none()
    if voucher is None:
        raise ResourceNotFound(f"Voucher '{voucher_id}' was not found.")
    items_result = await db.execute(select(JournalItem).where(JournalItem.voucher_id == voucher_id).order_by(JournalItem.line_order))
    items = list(items_result.scalars().all())
    ledger_ids = {i.ledger_id for i in items}
    ledgers_result = await db.execute(select(Ledger).where(Ledger.ledger_id.in_(ledger_ids))) if ledger_ids else None
    ledger_names = {l.ledger_id: l.name for l in ledgers_result.scalars().all()} if ledgers_result else {}

    return {
        "voucherId": voucher.voucher_id, "voucherNumber": voucher.voucher_number, "voucherType": voucher.voucher_type,
        "date": voucher.date, "referenceNumber": voucher.reference_number, "narration": voucher.narration,
        "totalAmountPaise": voucher.total_amount_paise, "isPosted": voucher.is_posted, "isCancelled": voucher.is_cancelled,
        "referenceVoucherId": voucher.reference_voucher_id,
        "journalLines": [
            {
                "ledgerId": i.ledger_id, "ledgerName": ledger_names.get(i.ledger_id, ""), "type": i.type,
                "amountPaise": i.amount_paise, "narration": i.narration, "lineOrder": i.line_order,
            }
            for i in items
        ],
    }


async def export_party(db: AsyncSession, company_id: str, party_id: str) -> dict:
    party = (await db.execute(select(Party).where(Party.company_id == company_id, Party.party_id == party_id))).scalar_one_or_none()
    if party is None:
        raise ResourceNotFound(f"Party '{party_id}' was not found.")
    payment_terms = f"CUSTOM:{party.payment_terms_custom_days or 0}" if party.payment_terms_type == "CUSTOM" else party.payment_terms_type
    return {
        "partyId": party.party_id, "ledgerId": party.ledger_id, "role": party.role, "entityType": party.entity_type,
        "displayName": party.display_name, "contactName": party.contact_name, "creditLimitPaise": party.credit_limit_paise,
        "paymentTerms": payment_terms, "isActive": party.is_active,
    }


async def export_ledger(db: AsyncSession, company_id: str, ledger_id: str) -> dict:
    ledger = (await db.execute(select(Ledger).where(Ledger.company_id == company_id, Ledger.ledger_id == ledger_id))).scalar_one_or_none()
    if ledger is None:
        raise ResourceNotFound(f"Ledger '{ledger_id}' was not found.")
    return {
        "ledgerId": ledger.ledger_id, "groupId": ledger.group_id, "name": ledger.name, "code": ledger.code,
        "openingBalancePaise": ledger.opening_balance_paise, "openingBalanceType": ledger.opening_balance_type,
        "currentBalancePaise": ledger.current_balance_paise, "currentBalanceType": ledger.current_balance_type,
        "gstin": ledger.gstin, "pan": ledger.pan, "stateCode": ledger.state_code, "address": ledger.address,
        "isSystem": ledger.is_system, "isActive": ledger.is_active,
    }


# ==================== Invoice export (thin wrapper over Phase 7D's assemble_document_data) ====================

async def export_invoice(db: AsyncSession, company_id: str, document_type: str, document_id: str) -> dict:
    data = await document_service.assemble_document_data(db, company_id, document_type, document_id)
    return {
        "documentId": document_id, "documentType": data["documentType"], "documentNumber": data["documentNumber"],
        "documentDate": data["date"], "dueDate": data["dueDate"], "sellerName": data["seller"]["name"],
        "buyerName": data["buyer"]["name"], "buyerGstin": data["buyer"]["gstin"], "lineCount": len(data["items"]),
        "taxableAmountPaise": data["totals"]["taxableAmountPaise"],
        "cgstPaise": data["tax"]["cgstPaise"], "sgstPaise": data["tax"]["sgstPaise"], "igstPaise": data["tax"]["igstPaise"],
        "cessPaise": data["tax"]["cessPaise"], "roundOffPaise": data["totals"]["roundOffPaise"],
        "grandTotalPaise": data["totals"]["grandTotalPaise"], "isPosted": data["isPosted"],
        "accountingVoucherNumber": data["accountingVoucherNumber"],
    }


# ==================== Report exports (thin re-shaping over application/queries/reports.py) ====================

async def export_trial_balance(db: AsyncSession, company_id: str, financial_year_id: str) -> dict:
    tb = await report_queries.trial_balance(db, company_id, financial_year_id)
    groups = (await db.execute(select(Group).where(Group.company_id == company_id))).scalars().all()
    group_info = {g.group_id: (g.name, g.primary_group) for g in groups}
    rows = []
    for r in tb["rows"]:
        name, primary_group = group_info.get(r["groupId"], ("", ""))
        rows.append({
            "ledgerId": r["ledgerId"], "ledgerName": r["ledgerName"], "groupId": r["groupId"], "groupName": name,
            "primaryGroup": primary_group, "openingDebitPaise": r["openingDebitPaise"], "openingCreditPaise": r["openingCreditPaise"],
            "transactionDebitPaise": r["debitPaise"], "transactionCreditPaise": r["creditPaise"],
            "closingDebitPaise": r["closingDebitPaise"], "closingCreditPaise": r["closingCreditPaise"],
        })
    return {
        "companyName": "", "financialYearCode": "", "asOfDate": None, "rows": rows,
        "totalClosingDebitPaise": tb["totalClosingDebitPaise"], "totalClosingCreditPaise": tb["totalClosingCreditPaise"],
        "isBalanced": tb["isBalanced"],
    }


async def export_profit_and_loss(db: AsyncSession, company_id: str, financial_year_id: str) -> dict:
    pnl = await report_queries.profit_and_loss(db, company_id, financial_year_id)
    return {
        "companyName": pnl["companyName"], "financialYearCode": pnl["financialYearCode"], "dateRange": "",
        "salesRevenuePaise": pnl["salesRevenuePaise"], "directIncomesPaise": pnl["directIncomesPaise"],
        "purchasesPaise": pnl["purchasesPaise"], "directExpensesPaise": pnl["directExpensesPaise"],
        "grossProfitPaise": pnl["grossProfitPaise"], "indirectIncomesPaise": pnl["indirectIncomesPaise"],
        "indirectExpensesPaise": pnl["indirectExpensesPaise"], "netProfitPaise": pnl["netProfitPaise"],
        "isInventoryAware": pnl["isInventoryAware"],
    }


async def export_balance_sheet(db: AsyncSession, company_id: str, financial_year_id: str) -> dict:
    bs = await report_queries.balance_sheet(db, company_id, financial_year_id)
    return {
        "companyName": bs["companyName"], "financialYearCode": bs["financialYearCode"], "asOfDate": bs["asOfDate"],
        "totalLiabilitiesPaise": bs["totalLiabilitiesPaise"], "totalAssetsPaise": bs["totalAssetsPaise"], "isBalanced": bs["isBalanced"],
        "capitalAccountsPaise": bs["capitalAccountsPaise"], "loansLiabilitiesPaise": bs["loansLiabilitiesPaise"],
        "currentLiabilitiesPaise": bs["currentLiabilitiesPaise"], "fixedAssetsPaise": bs["fixedAssetsPaise"],
        "currentAssetsPaise": bs["currentAssetsPaise"], "sundryDebtorsPaise": bs["sundryDebtorsPaise"],
        "bankAccountsPaise": bs["bankAccountsPaise"], "cashInHandPaise": bs["cashInHandPaise"], "stockInHandPaise": bs["stockInHandPaise"],
    }


async def export_outstanding(db: AsyncSession, company_id: str, role: str | None = None, today: str | None = None) -> dict:
    out = await report_queries.outstanding_report(db, company_id, role, today)
    rows = [
        {
            "invoiceId": r["invoiceId"], "invoiceNumber": r["invoiceNumber"], "invoiceType": r["invoiceType"],
            "partyId": r["partyId"], "partyName": r["partyName"], "voucherNumber": r["voucherNumber"],
            "date": r["date"], "dueDate": r["dueDate"], "totalAmountPaise": r["totalAmountPaise"],
            "outstandingAmountPaise": r["outstandingAmountPaise"], "status": r["status"],
            "daysOutstanding": r["daysOutstanding"], "agingBucket": r["agingBucket"],
        }
        for r in out["rows"]
    ]
    return {"rows": rows, "totalOutstandingPaise": out["totalOutstandingPaise"]}


async def export_gst_summary(db: AsyncSession, company_id: str, financial_year_id: str) -> dict:
    gst = await report_queries.gst_summary(db, company_id, financial_year_id)
    return {
        "companyName": "", "gstin": "", "period": financial_year_id,
        "totalTaxableOutwardPaise": gst["outward"]["taxablePaise"],
        "totalTaxOutwardPaise": gst["outward"]["cgstPaise"] + gst["outward"]["sgstPaise"] + gst["outward"]["igstPaise"],
        "totalTaxableInwardPaise": gst["inward"]["taxablePaise"],
        "totalTaxInwardItcPaise": gst["inward"]["cgstPaise"] + gst["inward"]["sgstPaise"] + gst["inward"]["igstPaise"],
        "netTaxPayablePaise": gst["netTaxPayablePaise"], "totalCessPaise": gst["outward"]["cessPaise"],
        "netCessPayablePaise": gst["netCessPayablePaise"],
    }


# ==================== GST transaction export / GSTR JSON ====================

async def export_gst_transactions(db: AsyncSession, company_id: str, financial_year_id: str) -> list[dict]:
    result = await db.execute(
        select(GstTransaction).where(GstTransaction.company_id == company_id, GstTransaction.financial_year_id == financial_year_id)
        .order_by(GstTransaction.created_at)
    )
    return [
        {
            "gstTransactionId": t.gst_transaction_id, "voucherId": t.voucher_id, "voucherType": t.voucher_type,
            "partyGstin": t.party_gstin, "placeOfSupply": t.place_of_supply, "supplyType": t.supply_type,
            "hsnSacCode": t.hsn_sac_code, "isService": None, "taxableAmountPaise": t.taxable_amount_paise,
            "gstRatePercent": t.gst_rate_percent, "cgstPaise": t.cgst_paise, "sgstPaise": t.sgst_paise,
            "igstPaise": t.igst_paise, "cessPaise": t.cess_paise, "direction": t.direction, "lineOrder": t.line_order,
        }
        for t in result.scalars().all()
    ]

"""POST_GST_TRANSACTION command handler (GST-Only Sync Path) - mirrors Android's
`AccountingRepository.postGstOnlySale`/`DatabaseTransaction.postGstOnlyTransactionsAtomic`. A
GST-only transaction has zero accounting effect (no Voucher/JournalItem row, no Ledger balance
change) - this handler never touches `apply_voucher_event`, the `vouchers` table, or the
`journal_items` table at all. Every row's `voucher_id` is persisted as NULL, exactly as the
Android event always sends it for this operation.

Company/financial-year scoping is enforced the same way [ledger_commands]/[invoice_commands]
already trust `event.companyId`/`event.financialYearId` for their own rows, plus one additional
check this operation's own phase spec calls for: the financial year must actually belong to the
event's company (InvalidFinancialYear if not) - every persisted GstTransaction row's `company_id`
is always the trusted `event.companyId`, never anything client-supplied per-line, so a GST
transaction can never reference another company's data.

Transaction/Contract Hardening: `voucher_type` is read directly from each line's own explicit
`voucherType` (Android's real `VoucherType` name, e.g. "SALES") - never derived from `direction`.
OUTPUT does not always mean SALES (a Credit Note is also OUTPUT-direction), so that derivation was
never a safe permanent contract. A line missing this field, or sending a value outside the
GST-relevant subset of the existing VoucherType set, is rejected via the existing structured error
contract - never silently guessed or defaulted.
"""
from __future__ import annotations

import time

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.errors import InvalidFinancialYear, ValidationError
from app.infrastructure.database.models import FinancialYear, GstTransaction
from app.schemas.sync import SyncEvent

# VoucherType is the existing, canonical accounting-transaction classification (server-side it is
# just the string value carried on Voucher.voucher_type) - this is NOT a second enum. It is
# narrowed here to the GST-relevant subset the phase spec calls out: RECEIPT/PAYMENT/CONTRA/
# JOURNAL are real VoucherType values but are never GST document types, so a GST-only event
# claiming one of them is rejected rather than silently accepted.
_GST_RELEVANT_VOUCHER_TYPES = {"SALES", "PURCHASE", "CREDIT_NOTE", "DEBIT_NOTE"}


def _now_ms() -> int:
    return int(time.time() * 1000)


async def apply_gst_transaction_event(db: AsyncSession, event: SyncEvent) -> dict:
    if not event.gstTransactions:
        raise ValidationError("A GST transaction event must include at least one GST transaction line.")

    if not event.financialYearId:
        raise InvalidFinancialYear("A GST transaction event must include a financial year id.")

    result = await db.execute(select(FinancialYear).where(FinancialYear.financial_year_id == event.financialYearId))
    fy = result.scalar_one_or_none()
    if fy is None or fy.company_id != event.companyId:
        raise InvalidFinancialYear(
            f"Financial year '{event.financialYearId}' does not belong to company '{event.companyId}'."
        )

    for line in event.gstTransactions:
        if not line.voucherType:
            raise ValidationError(
                f"GST transaction '{line.gstTransactionId}' must include an explicit voucherType classification."
            )
        if line.voucherType not in _GST_RELEVANT_VOUCHER_TYPES:
            raise ValidationError(
                f"'{line.voucherType}' is not a GST-relevant transaction type. Expected one of {sorted(_GST_RELEVANT_VOUCHER_TYPES)}."
            )

    now = _now_ms()
    persisted_ids: list[str] = []
    for line in event.gstTransactions:
        # D1b: a missing/blank transactionGroupId (an already-queued pre-D1b event) falls back to
        # this line's own gstTransactionId - a genuine single-row group, never fabricated. Mirrors
        # the Android migration's own COALESCE(voucher_id, gst_transaction_id) backfill exactly.
        group_id = line.transactionGroupId or line.gstTransactionId
        db.add(
            GstTransaction(
                gst_transaction_id=line.gstTransactionId,
                company_id=event.companyId,
                financial_year_id=event.financialYearId,
                voucher_id=None,
                voucher_type=line.voucherType,
                party_ledger_id=line.partyLedgerId,
                party_gstin=line.partyGstin,
                place_of_supply=line.placeOfSupply,
                supply_type=line.supplyType,
                item_id=line.itemId,
                hsn_sac_code=line.hsnSacCode,
                quantity_raw=line.quantityRaw,
                taxable_amount_paise=line.taxableAmountPaise,
                gst_rate_percent=line.gstRatePercent,
                cgst_paise=line.cgstPaise,
                sgst_paise=line.sgstPaise,
                igst_paise=line.igstPaise,
                cess_paise=line.cessPaise,
                direction=line.direction,
                line_order=line.lineOrder,
                created_at=now,
                charge_type=line.chargeType,
                supply_nature=line.supplyNature,
                transaction_group_id=group_id,
                transaction_date=line.transactionDate,
                party_gst_registration_status=line.partyGstRegistrationStatus,
            )
        )
        persisted_ids.append(line.gstTransactionId)

    return {"gstTransactionIds": persisted_ids, "status": "POSTED"}

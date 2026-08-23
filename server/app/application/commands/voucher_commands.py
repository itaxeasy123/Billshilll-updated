"""
Command handlers for POST_SALES_INVOICE / POST_PURCHASE_BILL / POST_RECEIPT / POST_PAYMENT /
POST_CONTRA / POST_JOURNAL / POST_CREDIT_NOTE / POST_DEBIT_NOTE / POST_VOUCHER / CANCEL_VOUCHER
(Phase 6, Priority 6.13) - one shared handler since every voucher-posting operation follows the
same shape (validate -> persist voucher + journal + stock + GST + settlement rows -> update ledger
balances), differing only in which extra domain check applies (Contra ledger restriction,
allocation over-allocation). This mirrors `VoucherPostingEngine.post()`'s authoritative posting
path as the server's OWN independent check - never trusts the client's balance/total claims.
"""
from __future__ import annotations

import time

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.accounting.allocation import validate_allocation
from app.domain.accounting.contra import validate_contra_ledgers
from app.domain.accounting.double_entry import JournalLineFacts, validate_balanced
from app.domain.errors import DuplicateVoucher, PeriodLocked, ValidationError
from app.infrastructure.database.models import (
    AccountingPeriod,
    GstTransaction,
    JournalItem,
    Ledger,
    SettlementAllocation,
    Voucher,
    VoucherStockLine,
)
from app.schemas.sync import SyncEvent


def _now_ms() -> int:
    return int(time.time() * 1000)


async def _apply_ledger_delta(db: AsyncSession, company_id: str, ledger_id: str, line_type: str, amount_paise: int) -> None:
    result = await db.execute(select(Ledger).where(Ledger.company_id == company_id, Ledger.ledger_id == ledger_id))
    ledger = result.scalar_one_or_none()
    if ledger is None:
        return  # Mirrors Android's own tolerant behavior - a line to an unknown ledger just skips the balance update.

    current_signed = ledger.current_balance_paise if ledger.current_balance_type == "DEBIT" else -ledger.current_balance_paise
    delta_signed = amount_paise if line_type == "DEBIT" else -amount_paise
    new_signed = current_signed + delta_signed
    ledger.current_balance_paise = abs(new_signed)
    ledger.current_balance_type = "DEBIT" if new_signed >= 0 else "CREDIT"


async def _compute_outstanding_paise(db: AsyncSession, company_id: str, invoice_voucher_id: str) -> int:
    result = await db.execute(select(Voucher).where(Voucher.company_id == company_id, Voucher.voucher_id == invoice_voucher_id))
    invoice = result.scalar_one_or_none()
    if invoice is None:
        return 0

    alloc_result = await db.execute(
        select(SettlementAllocation).where(SettlementAllocation.invoice_voucher_id == invoice_voucher_id)
    )
    allocated = sum(a.allocated_amount_paise for a in alloc_result.scalars().all())

    note_result = await db.execute(
        select(Voucher).where(Voucher.reference_voucher_id == invoice_voucher_id, Voucher.is_cancelled == False)  # noqa: E712
    )
    note_adjustment = sum(v.total_amount_paise for v in note_result.scalars().all())

    return invoice.total_amount_paise - allocated - note_adjustment


async def _validate_period_open(db: AsyncSession, company_id: str, financial_year_id: str, date: str) -> None:
    """Mirrors DoubleEntryValidator's period-lock check (Kotlin) - a voucher dated inside a LOCKED/
    AUDIT_LOCKED accounting period is rejected server-side too, independent of whether the posting
    device's own local check already caught it (defense in depth against a stale/tampered client)."""
    result = await db.execute(
        select(AccountingPeriod).where(
            AccountingPeriod.company_id == company_id,
            AccountingPeriod.financial_year_id == financial_year_id,
            AccountingPeriod.start_date <= date,
            AccountingPeriod.end_date >= date,
        )
    )
    period = result.scalar_one_or_none()
    if period is not None and period.status != "OPEN":
        raise PeriodLocked(f"Accounting period '{period.name}' is locked for date {date}.")


async def apply_voucher_event(db: AsyncSession, event: SyncEvent) -> dict:
    if event.voucher is None:
        raise ValidationError("A voucher event must include voucher data.")

    await _validate_period_open(db, event.companyId, event.financialYearId, event.voucher.date)

    # Double-entry validation - the server's own authoritative check, independent of whatever
    # totals the client believes it computed.
    validate_balanced([JournalLineFacts(l.ledgerId, l.type, l.amountPaise) for l in event.journalLines])

    now = _now_ms()

    if event.operation == "CANCEL_VOUCHER":
        # The voucher must already exist and not already be cancelled (mirrors
        # VoucherPostingEngine.cancel()'s double-cancellation guard) - the compensating reversal
        # journal lines below were already computed by Android; this just marks the header and
        # applies them, it never inserts a second Voucher row for the same voucher_id.
        result = await db.execute(select(Voucher).where(Voucher.company_id == event.companyId, Voucher.voucher_id == event.aggregateId))
        existing_voucher = result.scalar_one_or_none()
        if existing_voucher is None:
            raise ValidationError(f"Voucher '{event.aggregateId}' not found.")
        if existing_voucher.is_cancelled:
            raise ValidationError(f"Voucher '{existing_voucher.voucher_number}' is already cancelled.")
        existing_voucher.is_cancelled = True
        existing_voucher.updated_at = now
    else:
        # Duplicate voucher-id AND duplicate voucher-number guards (mirrors
        # AccountingDao.isVoucherNumberTaken) - defense in depth beyond the idempotency-key check,
        # for the case a different idempotency key somehow targets an already-posted voucher.
        id_clash = await db.execute(select(Voucher).where(Voucher.company_id == event.companyId, Voucher.voucher_id == event.voucher.voucherId))
        if id_clash.scalar_one_or_none() is not None:
            raise DuplicateVoucher(f"Voucher '{event.voucher.voucherId}' has already been posted.")

        number_clash = await db.execute(
            select(Voucher).where(
                Voucher.company_id == event.companyId,
                Voucher.financial_year_id == event.financialYearId,
                Voucher.voucher_number == event.voucher.voucherNumber,
            )
        )
        if number_clash.scalar_one_or_none() is not None:
            raise DuplicateVoucher(f"Voucher number '{event.voucher.voucherNumber}' already exists for this financial year.")

        # Contra restriction (server-side, independent of the Android UI/engine check)
        if event.operation == "POST_CONTRA":
            ledger_ids = [l.ledgerId for l in event.journalLines]
            result = await db.execute(select(Ledger).where(Ledger.ledger_id.in_(ledger_ids)))
            ledgers_by_id = {l.ledger_id: l for l in result.scalars().all()}
            validate_contra_ledgers([ledgers_by_id[lid].group_id for lid in ledger_ids if lid in ledgers_by_id])

        # Over-allocation guard for settlement events carrying allocations
        for settlement in event.settlements:
            if settlement.invoiceVoucherId:
                outstanding = await _compute_outstanding_paise(db, event.companyId, settlement.invoiceVoucherId)
                validate_allocation(settlement.allocatedAmountPaise, outstanding, settlement.invoiceVoucherId)

        db.add(
            Voucher(
                voucher_id=event.voucher.voucherId,
                company_id=event.companyId,
                financial_year_id=event.financialYearId,
                voucher_number=event.voucher.voucherNumber,
                voucher_type=event.voucher.voucherType,
                date=event.voucher.date,
                reference_number=event.voucher.referenceNumber,
                narration=event.voucher.narration,
                total_amount_paise=event.voucher.totalAmountPaise,
                is_posted=True,
                is_cancelled=False,
                created_at=now,
                updated_at=now,
                created_by=event.voucher.createdBy,
                party_gstin=event.voucher.partyGstin,
                is_gst_applicable=event.voucher.isGstApplicable,
                reference_voucher_id=event.voucher.referenceVoucherId,
                payment_mode=event.voucher.paymentMode,
            )
        )

    for line in event.journalLines:
        db.add(
            JournalItem(
                item_id=line.itemId, voucher_id=event.aggregateId, company_id=event.companyId,
                financial_year_id=event.financialYearId, ledger_id=line.ledgerId, type=line.type,
                amount_paise=line.amountPaise, narration=line.narration, line_order=line.lineOrder,
            )
        )
        await _apply_ledger_delta(db, event.companyId, line.ledgerId, line.type, line.amountPaise)

    for stock_line in event.stockLines:
        db.add(
            VoucherStockLine(
                line_id=stock_line.lineId, voucher_id=event.aggregateId, company_id=event.companyId,
                financial_year_id=event.financialYearId, item_id=stock_line.itemId, direction=stock_line.direction,
                quantity_raw=stock_line.quantityRaw, rate_paise=stock_line.ratePaise,
                amount_paise=stock_line.amountPaise, line_order=stock_line.lineOrder,
            )
        )

    for gst_line in event.gstTransactions:
        db.add(
            GstTransaction(
                gst_transaction_id=gst_line.gstTransactionId, company_id=event.companyId,
                financial_year_id=event.financialYearId, voucher_id=event.aggregateId,
                voucher_type=event.voucher.voucherType, party_ledger_id=gst_line.partyLedgerId,
                party_gstin=gst_line.partyGstin, place_of_supply=gst_line.placeOfSupply,
                supply_type=gst_line.supplyType, item_id=gst_line.itemId, hsn_sac_code=gst_line.hsnSacCode,
                quantity_raw=gst_line.quantityRaw, taxable_amount_paise=gst_line.taxableAmountPaise,
                gst_rate_percent=gst_line.gstRatePercent, cgst_paise=gst_line.cgstPaise,
                sgst_paise=gst_line.sgstPaise, igst_paise=gst_line.igstPaise, cess_paise=gst_line.cessPaise,
                direction=gst_line.direction, line_order=gst_line.lineOrder, created_at=now,
            )
        )

    for settlement in event.settlements:
        db.add(
            SettlementAllocation(
                allocation_id=settlement.allocationId, company_id=event.companyId,
                financial_year_id=event.financialYearId, settlement_voucher_id=settlement.settlementVoucherId,
                invoice_voucher_id=settlement.invoiceVoucherId, allocated_amount_paise=settlement.allocatedAmountPaise,
                created_at=now,
            )
        )

    return {"voucherId": event.voucher.voucherId, "status": "CANCELLED" if event.operation == "CANCEL_VOUCHER" else "POSTED"}

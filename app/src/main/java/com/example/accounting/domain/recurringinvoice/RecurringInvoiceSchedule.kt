package com.example.accounting.domain.recurringinvoice

import com.example.accounting.domain.invoice.InvoiceLine
import com.example.accounting.domain.invoice.InvoiceType
import com.example.accounting.domain.recurring.RecurringFrequency
import com.example.accounting.domain.recurring.RecurringVoucherPeriod
import java.time.LocalDate

/**
 * Recurring Invoice generation (Phase 7J) - architecture and models only, no wiring into
 * `AccountingRepository`/`AccountingScheduler`, no Room entity, no real implementation. Mirrors
 * [com.example.accounting.domain.recurring.RecurringVoucherSchedule]'s draft-first shape and
 * safety reasoning, with one structural simplification specific to Invoice: unlike `Voucher`
 * (which had no draft concept before Phase 7F built one from scratch -
 * `RecurringVoucherDraftEntity`), [com.example.accounting.domain.invoice.Invoice] **already is**
 * its own draft - it has zero Ledger/JournalItem/Trial-Balance/P&L effect until
 * `AccountingRepository.postInvoice` sets its `voucherId` (Phase 7A). A future implementation of
 * this schedule therefore needs no new draft table for the invoice itself - it only needs to call
 * the existing, unmodified `AccountingRepository.createDraftInvoice` to produce a real, ordinary
 * DRAFT `Invoice` a human then reviews, edits, discards, or posts through the existing,
 * unmodified `postInvoice` - exactly like every other invoice in this app. There is no second
 * invoice-creation mechanism, and none is introduced here.
 *
 * What a future implementation phase would still need to add (not built in this architecture-only
 * pass): a thin `AccountingRepository.generateInvoiceIfDue`-style function following
 * `generateRecurringVoucherIfDue`'s exact pattern, and a small idempotency-tracking table (this
 * schedule's own `(scheduleId, periodKey)`, unique-indexed, storing the resulting `invoiceId` -
 * the same shape `RecurringVoucherDraftEntity` already established) so a monthly cycle run twice
 * cannot propose the same retainer invoice twice. Reuses [RecurringFrequency] and
 * [RecurringVoucherPeriod.periodKeyFor] directly from the existing, frozen Phase 7F recurring-
 * voucher domain rather than duplicating that logic - only the schedule-specific `isDue` check
 * (which needs this type's own fields) is a small, deliberate parallel to
 * [RecurringVoucherPeriod.isDue], the same kind of acceptable, documented duplication already used
 * for `BankLineDirection` vs. `DrCr`.
 */
data class RecurringInvoiceSchedule(
    val scheduleId: String,
    val companyId: String,
    val financialYearId: String,
    val partyId: String,
    val invoiceType: InvoiceType,
    val frequency: RecurringFrequency,
    val dayOfMonth: Int,
    val narration: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val lines: List<InvoiceLine> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Outcome of one generation attempt - never an exception for the two routine "nothing happened"
 * cases, matching [com.example.accounting.domain.recurring.RecurringVoucherGenerationOutcome]'s
 * exact reasoning. [InvoiceDraftGenerated] carries a real `invoiceId` - a genuine, ordinary DRAFT
 * `Invoice` row - not a separate draft type, since Invoice already provides that state itself. */
sealed class RecurringInvoiceGenerationOutcome {
    data class InvoiceDraftGenerated(val invoiceId: String, val periodKey: String) : RecurringInvoiceGenerationOutcome()
    data class AlreadyGenerated(val periodKey: String) : RecurringInvoiceGenerationOutcome()
    data class NotDue(val reason: String) : RecurringInvoiceGenerationOutcome()
}

/** Pure, DAO-independent scheduling logic - mirrors
 * [com.example.accounting.domain.recurring.RecurringVoucherPeriod.isDue] exactly (same
 * day-of-month clamping for short months, same YEARLY-anniversary logic), reimplemented here only
 * because it must operate on [RecurringInvoiceSchedule]'s own fields rather than
 * `RecurringVoucherSchedule`'s. */
object RecurringInvoicePeriod {
    fun isDue(schedule: RecurringInvoiceSchedule, asOfDate: LocalDate): Boolean {
        if (!schedule.isActive) return false
        if (asOfDate.isBefore(schedule.startDate)) return false
        if (schedule.endDate != null && asOfDate.isAfter(schedule.endDate)) return false

        val scheduledDayThisMonth = schedule.dayOfMonth.coerceAtMost(asOfDate.lengthOfMonth())
        return when (schedule.frequency) {
            RecurringFrequency.MONTHLY -> asOfDate.dayOfMonth >= scheduledDayThisMonth
            RecurringFrequency.YEARLY -> {
                val anniversaryMonth = schedule.startDate.monthValue
                asOfDate.monthValue > anniversaryMonth ||
                    (asOfDate.monthValue == anniversaryMonth && asOfDate.dayOfMonth >= scheduledDayThisMonth)
            }
        }
    }

    /** Delegates directly to the existing, frozen [RecurringVoucherPeriod.periodKeyFor] - this
     * part of the logic has no `RecurringVoucherSchedule`-specific coupling, so it's reused
     * verbatim rather than duplicated. */
    fun periodKeyFor(frequency: RecurringFrequency, date: LocalDate): String =
        RecurringVoucherPeriod.periodKeyFor(frequency, date)
}

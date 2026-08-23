package com.example.accounting.domain.recurring

import com.example.accounting.core.common.DrCr
import com.example.accounting.domain.accounting.VoucherType
import java.time.LocalDate

/**
 * Recurring Voucher Engine (Phase 7F, "B") - **draft-first**. A schedule is a TEMPLATE only; it
 * never posts anything itself, and neither does generation.
 * [com.example.accounting.data.repository.AccountingRepository.generateRecurringVoucherIfDue]
 * turns a due schedule into a [RecurringVoucherDraft] - a plain proposed voucher with **no
 * journal effect, no ledger effect, no balance effect, no GST effect, no inventory movement**.
 * Only [com.example.accounting.data.repository.AccountingRepository.postRecurringVoucherDraft],
 * called exclusively in response to an explicit user "Post" action, builds a
 * [com.example.accounting.domain.accounting.Voucher] from the (possibly user-edited) draft and
 * calls the existing, unmodified `AccountingRepository.postVoucher` - the exact same validation
 * (`DoubleEntryValidator`, period/tenant checks) and posting path every other voucher in this app
 * already goes through. There is no second posting mechanism anywhere in this feature, and no
 * code path exists for automation to post a recurring voucher on its own:
 *
 * ```
 * Recurring Schedule -> Recurring Voucher Candidate -> Draft Voucher -> User Review/Edit
 *                                                                            |-- Edit
 *                                                                            |-- Delete/Discard
 *                                                                            `-- Post
 *                                                                                 -> DoubleEntryValidator
 *                                                                                 -> VoucherPostingEngine
 *                                                                                 -> Ledger
 * ```
 */
enum class RecurringFrequency { MONTHLY, YEARLY }

data class RecurringVoucherLine(
    val ledgerId: String,
    val type: DrCr,
    val amountPaise: Long,
    val narration: String = "",
    val lineOrder: Int = 0
)

/** [dayOfMonth] is clamped to the target month's actual length when generating (e.g. a schedule
 * with `dayOfMonth = 31` still generates in February, on the 28th/29th) - never a spurious
 * "invalid day" failure for a schedule that's perfectly valid every other month. */
data class RecurringVoucherSchedule(
    val scheduleId: String,
    val companyId: String,
    val financialYearId: String,
    val name: String,
    val voucherType: VoucherType,
    val frequency: RecurringFrequency,
    val dayOfMonth: Int,
    val narration: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val lines: List<RecurringVoucherLine> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalAmountPaise: Long get() = lines.filter { it.type == DrCr.DEBIT }.sumOf { it.amountPaise }
}

/** A draft's lifecycle: [PENDING_REVIEW] (just generated, awaiting the user), [POSTED] (the user
 * pressed Post - [RecurringVoucherDraft.generatedVoucherId] is then set), or [DISCARDED] (the user
 * rejected it). [POSTED] and [DISCARDED] are both terminal - once a schedule+period has been
 * decided on, the draft row is kept (never hard-deleted) as an audit trail and the unique
 * `(scheduleId, periodKey)` slot stays permanently claimed, so that period is never re-proposed. */
enum class RecurringVoucherDraftStatus { PENDING_REVIEW, POSTED, DISCARDED }

data class RecurringVoucherDraftLine(
    val ledgerId: String,
    val type: DrCr,
    val amountPaise: Long,
    val narration: String = "",
    val lineOrder: Int = 0
)

/**
 * A generated, review-only candidate voucher (Phase 7F, "B"). Deliberately **not** a
 * [com.example.accounting.domain.accounting.Voucher] and never stored in the `vouchers` table -
 * this is what makes "no journal/ledger/balance/GST/inventory effect until Post" a structural
 * guarantee rather than a convention: nothing in this type or its persistence touches any table
 * `postVoucher` writes to.
 */
data class RecurringVoucherDraft(
    val draftId: String,
    val companyId: String,
    val scheduleId: String,
    val financialYearId: String,
    val periodKey: String,
    val voucherType: VoucherType,
    val date: LocalDate,
    val narration: String = "",
    val lines: List<RecurringVoucherDraftLine> = emptyList(),
    val status: RecurringVoucherDraftStatus = RecurringVoucherDraftStatus.PENDING_REVIEW,
    val generatedVoucherId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** The outcome of one `generateRecurringVoucherIfDue` call - never an exception for the two
 * entirely normal "nothing happened" cases ([NotDue]/[AlreadyGenerated]), since calling this
 * function on a day/schedule combination that isn't due, or that already generated this period's
 * draft, is expected routine behavior for a daily/monthly automation cycle, not an error.
 * [DraftGenerated] carries only a draftId - never a voucherId, since no voucher exists yet. */
sealed class RecurringVoucherGenerationOutcome {
    data class DraftGenerated(val draftId: String, val periodKey: String) : RecurringVoucherGenerationOutcome()
    data class AlreadyGenerated(val periodKey: String) : RecurringVoucherGenerationOutcome()
    data class NotDue(val reason: String) : RecurringVoucherGenerationOutcome()
}

/**
 * Pure, DAO-independent scheduling logic - a schedule's due-ness and its period key
 * (`"2026-06"` for MONTHLY, `"2026"` for YEARLY) are computed here, never inline at a call site,
 * so idempotency ("a monthly task cannot generate the same rent/depreciation voucher twice") has
 * one single, testable source of truth for "which period does this date belong to."
 */
object RecurringVoucherPeriod {
    fun periodKeyFor(frequency: RecurringFrequency, date: LocalDate): String = when (frequency) {
        RecurringFrequency.MONTHLY -> "%04d-%02d".format(date.year, date.monthValue)
        RecurringFrequency.YEARLY -> "%04d".format(date.year)
    }

    fun isDue(schedule: RecurringVoucherSchedule, asOfDate: LocalDate): Boolean {
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
}

package com.example.accounting.domain.taxation.gst

/**
 * GST compliance-period governance (Phase 5, Priority 10) - deliberately separate from
 * [com.example.accounting.domain.financialyear.AccountingPeriod]. A GST filing period can be
 * locked for return-filing purposes without touching accounting-period locking at all: nothing in
 * [com.example.accounting.domain.accounting.DoubleEntryValidator],
 * [com.example.accounting.core.database.VoucherPostingEngine], or the accounting-period lock/unlock
 * path reads this type. Locking a GST filing period is purely a compliance-tracking flag today -
 * it does not (and must not) reject voucher posting.
 */
data class GstFilingPeriod(
    val filingPeriodId: String,
    val companyId: String,
    val periodLabel: String,
    val startDate: String,
    val endDate: String,
    val isLocked: Boolean,
    val lockedAt: Long?,
    val lockedBy: String?
)

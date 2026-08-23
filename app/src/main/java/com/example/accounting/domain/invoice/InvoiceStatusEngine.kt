package com.example.accounting.domain.invoice

import java.time.LocalDate

/**
 * The single source of truth for an Invoice's lifecycle status (Phase 7A) - never a stored,
 * UI-settable field. Room-independent and pure, the same pattern as
 * [com.example.accounting.domain.accounting.RoundOffEngine]: every input is a plain value the
 * caller already has on hand (the existing `computeOutstandingPaise` result, the linked Voucher's
 * `isCancelled`/total), so this composes existing frozen data rather than reimplementing anything.
 * The UI (Phase 7J) only ever displays this result; it never sets a status directly.
 */
object InvoiceStatusEngine {

    fun deriveStatus(
        voucherId: String?,
        isCancelled: Boolean,
        totalAmountPaise: Long,
        outstandingPaise: Long,
        dueDate: LocalDate?,
        today: LocalDate = LocalDate.now()
    ): InvoiceStatus {
        if (voucherId == null) return InvoiceStatus.DRAFT
        if (isCancelled) return InvoiceStatus.CANCELLED

        val base = when {
            outstandingPaise <= 0L -> InvoiceStatus.PAID
            outstandingPaise < totalAmountPaise -> InvoiceStatus.PARTIALLY_PAID
            else -> InvoiceStatus.POSTED
        }

        if (base != InvoiceStatus.PAID && dueDate != null && today.isAfter(dueDate)) {
            return InvoiceStatus.OVERDUE
        }
        return base
    }
}

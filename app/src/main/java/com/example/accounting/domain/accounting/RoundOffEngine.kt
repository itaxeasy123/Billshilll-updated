package com.example.accounting.domain.accounting

import com.example.accounting.core.common.Money

/**
 * Rounds an invoice grand total to the nearest rupee, the standard Indian invoicing convention
 * (Phase 5, Priority 7). Deliberately engine-only - [com.example.accounting.domain.trading.TradingWorkflowEngine]
 * calls this after computing the raw taxable+tax total; the UI never types in a Round Off figure.
 *
 * [RoundOffResult.roundOffAmount] is `roundedTotal - rawTotal`: positive when rounding up, negative
 * when rounding down, zero when the raw total already lands exactly on a rupee (the common case -
 * callers should skip posting a Round Off line entirely when this is zero).
 */
object RoundOffEngine {

    data class RoundOffResult(
        val rawTotal: Money,
        val roundedTotal: Money,
        val roundOffAmount: Money
    )

    fun roundInvoiceTotal(rawTotal: Money): RoundOffResult {
        val rupees = Math.round(rawTotal.paise / 100.0)
        val roundedTotal = Money.fromRupees(rupees)
        return RoundOffResult(
            rawTotal = rawTotal,
            roundedTotal = roundedTotal,
            roundOffAmount = roundedTotal - rawTotal
        )
    }
}

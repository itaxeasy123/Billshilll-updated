package com.example.accounting.domain.inventory.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure weighted-average costing math (Section 6 of the Phase 4 spec - only WEIGHTED_AVERAGE is
 * implemented; FIFO was explicitly deferred rather than built speculatively).
 *
 * All quantities are in thousandths (see [com.example.accounting.core.common.Quantity]); rates
 * and costs are in paise per whole unit. BigDecimal is used only for the rounding division step,
 * consistent with [com.example.accounting.core.common.Money.percentage] elsewhere in the project
 * (Project Principle 1 permits BigDecimal for intermediate calculations with explicit scale and
 * HALF_EVEN rounding - integer paise is still the only value that is ever stored).
 */
object StockValuationEngine {

    /**
     * New weighted-average cost per unit (paise) after receiving [inQtyRaw] units at [inRatePaise]
     * into an existing position of [currentQtyRaw] units valued at [currentAvgCostPaise] each.
     */
    fun weightedAverageCostAfterReceipt(
        currentQtyRaw: Long,
        currentAvgCostPaise: Long,
        inQtyRaw: Long,
        inRatePaise: Long
    ): Long {
        val newTotalQtyRaw = currentQtyRaw + inQtyRaw
        if (newTotalQtyRaw <= 0L) return 0L
        val currentValue = BigDecimal(currentQtyRaw) * BigDecimal(currentAvgCostPaise)
        val incomingValue = BigDecimal(inQtyRaw) * BigDecimal(inRatePaise)
        return (currentValue + incomingValue)
            .divide(BigDecimal(newTotalQtyRaw), 0, RoundingMode.HALF_EVEN)
            .toLong()
    }

    /** amount = quantity (thousandths) x rate (paise/unit), rounded to the nearest paise. */
    fun amountFor(quantityRaw: Long, ratePaise: Long): Long =
        BigDecimal(quantityRaw).multiply(BigDecimal(ratePaise))
            .divide(BigDecimal(1000L), 0, RoundingMode.HALF_EVEN)
            .toLong()
}

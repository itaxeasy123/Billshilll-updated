package com.example.accounting.domain.inventory.engine

import com.example.accounting.data.local.entity.StockMovementEntity
import com.example.accounting.domain.inventory.StockDirection
import com.example.accounting.domain.inventory.StockMovementType

/**
 * Pure, read-only Cost of Goods Sold computation (Section 5 of the Phase 4 spec):
 *
 *   COGS = Opening Stock + Purchases (at cost) - Purchase Returns (at cost) - Closing Stock
 *
 * "Direct Purchase Costs" (e.g. freight-in) from the spec formula are not modeled as a separate
 * line - there is no such movement type today, so any landed cost must be included in the
 * purchase line's rate itself. Documented as a known simplification rather than invented.
 *
 * Never trusts [com.example.accounting.data.local.entity.StockItemEntity]'s cached
 * currentQuantity/currentAvgCostPaise for anything but a "right now" figure - for any historical
 * or date-ranged report it replays the immutable movement history instead, the same way Phase 3's
 * Trial Balance/P&L never rely on a cached ledger balance for a point-in-time cut.
 */
object CogsEngine {

    data class ItemValuation(val quantityRaw: Long, val avgCostPaise: Long) {
        val valuePaise: Long get() = StockValuationEngine.amountFor(quantityRaw, avgCostPaise)
    }

    data class CogsResult(
        val openingStockPaise: Long,
        val closingStockPaise: Long,
        val purchasesAtCostPaise: Long,
        val purchaseReturnsAtCostPaise: Long,
        val cogsPaise: Long
    )

    /**
     * Replays a single item's chronologically-ordered movements starting from
     * [openingQuantityRaw]/[openingRatePaise], returning its valuation after all of them.
     */
    fun replayItemValuation(
        openingQuantityRaw: Long,
        openingRatePaise: Long,
        movements: List<StockMovementEntity>
    ): ItemValuation {
        var qty = openingQuantityRaw
        var avgCost = openingRatePaise
        for (m in movements.sortedWith(compareBy({ it.date }, { it.createdAt }))) {
            when (m.direction) {
                StockDirection.IN -> {
                    avgCost = StockValuationEngine.weightedAverageCostAfterReceipt(qty, avgCost, m.quantityRaw, m.ratePaise)
                    qty += m.quantityRaw
                }
                StockDirection.OUT -> qty -= m.quantityRaw // weighted-average cost is unchanged on issue
            }
        }
        return ItemValuation(qty, avgCost)
    }

    /**
     * COGS for one item over a period. [movementsBeforePeriod] establishes the opening position
     * on top of the item's true opening qty/rate (empty for a full-FY report, since the item's
     * opening balance already IS the FY-start position); [movementsInPeriod] is what's replayed
     * to reach the closing position and to total Purchases/Purchase Returns.
     */
    fun computeForItem(
        openingQuantityRaw: Long,
        openingRatePaise: Long,
        movementsBeforePeriod: List<StockMovementEntity>,
        movementsInPeriod: List<StockMovementEntity>
    ): CogsResult {
        val openingOfPeriod = replayItemValuation(openingQuantityRaw, openingRatePaise, movementsBeforePeriod)
        val closingOfPeriod = replayItemValuation(openingOfPeriod.quantityRaw, openingOfPeriod.avgCostPaise, movementsInPeriod)

        val purchasesPaise = movementsInPeriod.filter { it.movementType == StockMovementType.PURCHASE }.sumOf { it.amountPaise }
        val purchaseReturnsPaise = movementsInPeriod.filter { it.movementType == StockMovementType.PURCHASE_RETURN }.sumOf { it.amountPaise }

        val openingValue = openingOfPeriod.valuePaise
        val closingValue = closingOfPeriod.valuePaise
        val cogs = openingValue + purchasesPaise - purchaseReturnsPaise - closingValue

        return CogsResult(openingValue, closingValue, purchasesPaise, purchaseReturnsPaise, cogs)
    }

    fun aggregate(results: List<CogsResult>): CogsResult = CogsResult(
        openingStockPaise = results.sumOf { it.openingStockPaise },
        closingStockPaise = results.sumOf { it.closingStockPaise },
        purchasesAtCostPaise = results.sumOf { it.purchasesAtCostPaise },
        purchaseReturnsAtCostPaise = results.sumOf { it.purchaseReturnsAtCostPaise },
        cogsPaise = results.sumOf { it.cogsPaise }
    )
}

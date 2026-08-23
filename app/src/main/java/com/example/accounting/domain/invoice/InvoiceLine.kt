package com.example.accounting.domain.invoice

import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity

/**
 * A draft-invoice line item (Phase 7A) - the pre-posting working copy of what becomes real
 * journal/stock/GST rows once the parent [Invoice] is posted through the existing, unchanged
 * [com.example.accounting.domain.trading.TradingWorkflowEngine]. Mirrors that engine's
 * `TradingLineInput` shape so building the posting call from a draft is a direct field mapping.
 */
data class InvoiceLine(
    val lineId: String,
    val itemId: String,
    val itemName: String,
    val hsnSacCode: String,
    val quantity: Quantity,
    val rate: Money,
    val gstRatePercent: Double,
    val cessRatePercent: Double = 0.0,
    val lineOrder: Int = 0
)

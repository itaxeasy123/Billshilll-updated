package com.example.accounting.domain.document

import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity

/**
 * A [TradeDocument] line item (Phase 7B) - mirrors
 * [com.example.accounting.domain.invoice.InvoiceLine]'s shape exactly, so converting a
 * TradeDocument into a draft Invoice (or another TradeDocument) is a direct field mapping.
 */
data class TradeDocumentLine(
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

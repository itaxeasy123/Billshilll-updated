package com.example.accounting.domain.inventory

import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity

data class StockItem(
    val itemId: String,
    val companyId: String,
    val name: String,
    val sku: String = "",
    val hsnCode: String = "",
    val unit: String = "Nos",
    val gstRatePercent: Double = 18.0,
    val openingQuantity: Quantity = Quantity.ZERO,
    val openingRate: Money = Money.ZERO,
    val currentQuantity: Quantity = Quantity.ZERO,
    val standardCost: Money = Money.ZERO,
    val standardSellingPrice: Money = Money.ZERO
)

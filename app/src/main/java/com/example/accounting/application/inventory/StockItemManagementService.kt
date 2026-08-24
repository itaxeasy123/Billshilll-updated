package com.example.accounting.application.inventory

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.inventory.StockItem
import kotlinx.coroutines.flow.Flow

/**
 * Application-service facade for Stock Item management (Phase 7J-B, "Party/Ledger/Item") - a thin,
 * pure-delegation wrapper over the existing, unmodified [AccountingRepository.createStockItem]/
 * [AccountingRepository.getStockItems]. Never recomputes weighted-average cost or any inventory
 * figure itself - that stays exclusively in the frozen `InventoryEngine`/`StockValuationEngine`.
 */
class StockItemManagementService(private val repository: AccountingRepository) {

    suspend fun createStockItem(item: StockItem): AccountingResult<StockItem> =
        repository.createStockItem(item)

    fun getStockItems(companyId: String): Flow<List<StockItem>> =
        repository.getStockItems(companyId)
}

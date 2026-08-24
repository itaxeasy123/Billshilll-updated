package com.example.accounting.application.qrbarcode

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.inventory.StockItem
import com.example.accounting.domain.qrbarcode.BarcodeGenerationResult
import com.example.accounting.domain.qrbarcode.BarcodeScanSuggestion
import com.example.accounting.domain.qrbarcode.QrBarcodeAdapter
import com.example.accounting.domain.rendering.BusinessProfile
import kotlinx.coroutines.flow.first

/**
 * Application-service orchestration over [QrBarcodeAdapter] (Phase 7J-B) - resolves a [StockItem]
 * by id (via the existing, unmodified [AccountingRepository.getStockItems]) before delegating to
 * the adapter, since [QrBarcodeAdapter.generateForStockItem] itself takes a full [StockItem]
 * object, never an id it would look up on its own. A thin resolve-then-delegate wrapper - never
 * creates or scans anything itself.
 */
class QrBarcodeManagementService(
    private val repository: AccountingRepository,
    private val adapter: QrBarcodeAdapter
) {

    suspend fun generateForStockItem(
        requestingCompany: BusinessProfile,
        companyId: String,
        stockItemId: String
    ): AccountingResult<BarcodeGenerationResult> {
        val stockItem = repository.getStockItems(companyId).first().firstOrNull { it.itemId == stockItemId }
            ?: return AccountingResult.Failure(AppError.ResourceNotFound("StockItem", stockItemId))
        return adapter.generateForStockItem(requestingCompany, stockItem)
    }

    /** [candidateStockItems] defaults to the whole company's stock catalog when not explicitly
     * supplied by the caller - the adapter itself never queries the database. */
    suspend fun scanImage(
        requestingCompany: BusinessProfile,
        companyId: String,
        imageAssetId: String,
        candidateStockItems: List<StockItem>? = null
    ): AccountingResult<BarcodeScanSuggestion> {
        val candidates = candidateStockItems ?: repository.getStockItems(companyId).first()
        return adapter.scanImage(requestingCompany, imageAssetId, candidates)
    }
}

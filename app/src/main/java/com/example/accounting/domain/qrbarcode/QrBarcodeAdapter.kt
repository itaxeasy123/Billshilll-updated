package com.example.accounting.domain.qrbarcode

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.domain.inventory.StockItem
import com.example.accounting.domain.rendering.BusinessProfile

/** Which barcode/QR symbology a payload uses. [UNKNOWN] covers any scanner-reported symbology
 * this closed set doesn't already name - never silently coerced into a guess. */
enum class BarcodeSymbology { QR_CODE, CODE_128, EAN_13, UNKNOWN }

/** A decoded (or to-be-encoded) barcode/QR value - just the raw payload, never an image. Image
 * rendering/decoding itself is Android-framework work, explicitly out of scope for this
 * architecture-only contract. */
data class BarcodePayload(
    val symbology: BarcodeSymbology,
    val rawValue: String
)

/** Result of generating a barcode/QR payload for an existing [StockItem]. */
data class BarcodeGenerationResult(
    val stockItemId: String,
    val payload: BarcodePayload
)

/** Result of scanning an image for a barcode/QR - [matchedStockItemId] is a *suggestion*, null
 * when nothing in [candidateStockItems] matched well enough; never an instruction to create a new
 * item. */
data class BarcodeScanSuggestion(
    val rawValue: String,
    val symbology: BarcodeSymbology,
    val matchedStockItemId: String?,
    val confidenceScore: Double
)

/**
 * Adapter boundary for QR/barcode generation and scanning (Phase 7J) - a pure Kotlin interface
 * only, no implementation, no barcode/ZXing/ML-Kit library dependency, no Android dependency.
 * Mirrors [com.example.accounting.domain.ocr.OcrIngestionAdapter]'s exact shape and safety
 * boundary.
 *
 * [generateForStockItem] takes the [StockItem] itself (not just an id) so this interface has zero
 * `AccountingDao`/`AccountingRepository` access - same structural guarantee every 7H/7I/7J
 * contract already carries. [scanImage] references an already-uploaded
 * [com.example.accounting.domain.rendering.DocumentAsset] by id and takes [candidateStockItems]
 * as an explicit parameter, exactly like [com.example.accounting.domain.reconciliation.ReconciliationAdapter]'s
 * `candidateVouchers` - it can only ever propose a match from what its caller hands it, never
 * query the database itself, and never create a new [StockItem] on a scan.
 */
interface QrBarcodeAdapter {
    suspend fun generateForStockItem(
        requestingCompany: BusinessProfile,
        stockItem: StockItem
    ): AccountingResult<BarcodeGenerationResult>

    suspend fun scanImage(
        requestingCompany: BusinessProfile,
        imageAssetId: String,
        candidateStockItems: List<StockItem>
    ): AccountingResult<BarcodeScanSuggestion>
}

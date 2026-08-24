package com.example.accounting.data.qrbarcode

import android.graphics.BitmapFactory
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.domain.inventory.StockItem
import com.example.accounting.domain.qrbarcode.BarcodeGenerationResult
import com.example.accounting.domain.qrbarcode.BarcodePayload
import com.example.accounting.domain.qrbarcode.BarcodeScanSuggestion
import com.example.accounting.domain.qrbarcode.BarcodeSymbology
import com.example.accounting.domain.qrbarcode.QrBarcodeAdapter
import com.example.accounting.domain.rendering.BusinessProfile
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

/**
 * Real QR/barcode implementation of [QrBarcodeAdapter] (Phase 7J-B), backed by ZXing `core` only
 * (no camera/Android-embedded dependency - a live camera preview was never in scope, matching the
 * interface's original "no barcode/ML-Kit library" baseline; this only adds the pure-payload
 * encode/decode half). Lives in `data/`, not `domain/`, since [scanImage] needs
 * `android.graphics.BitmapFactory` to decode a stored image file - the same layering reason
 * `data/rendering/PdfDocumentRenderer.kt` lives in `data/` (Phase 7D). Reads the referenced
 * [com.example.accounting.domain.rendering.DocumentAsset] via [dao] directly (a benign lookup read,
 * matching the interface's own `imageAssetId: String` shape, which already expects the
 * implementation to resolve the id itself - exactly mirroring
 * [com.example.accounting.data.dataimport.CsvJsonDataImportAdapter]'s identical pattern). Pure
 * utility - zero accounting logic anywhere in this file: [scanImage] never creates a [StockItem],
 * only ever a *suggestion*, exactly as the frozen interface promises.
 */
class ZxingQrBarcodeAdapter(private val dao: AccountingDao) : QrBarcodeAdapter {

    override suspend fun generateForStockItem(
        requestingCompany: BusinessProfile,
        stockItem: StockItem
    ): AccountingResult<BarcodeGenerationResult> {
        val rawValue = buildPayload(stockItem)
        return try {
            // Real ZXing encode as a correctness check even though BarcodePayload carries only the
            // raw string, never an image (per the frozen domain type) - an unencodable payload is a
            // genuine validation failure, never silently accepted.
            QRCodeWriter().encode(rawValue, BarcodeFormat.QR_CODE, 200, 200)
            AccountingResult.Success(BarcodeGenerationResult(stockItem.itemId, BarcodePayload(BarcodeSymbology.QR_CODE, rawValue)))
        } catch (e: WriterException) {
            AccountingResult.Failure(AppError.ValidationError("Stock item '${stockItem.itemId}' could not be encoded as a QR payload: ${e.message}"))
        }
    }

    override suspend fun scanImage(
        requestingCompany: BusinessProfile,
        imageAssetId: String,
        candidateStockItems: List<StockItem>
    ): AccountingResult<BarcodeScanSuggestion> {
        val asset = dao.getDocumentAssetById(requestingCompany.companyId, imageAssetId)
            ?: return AccountingResult.Failure(AppError.ResourceNotFound("DocumentAsset", imageAssetId))
        val bitmap = BitmapFactory.decodeFile(File(asset.storageReference).absolutePath)
            ?: return AccountingResult.Failure(AppError.ValidationError("Image asset '${asset.storageReference}' could not be decoded."))

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        val result = try {
            MultiFormatReader().decode(binaryBitmap)
        } catch (e: NotFoundException) {
            return AccountingResult.Failure(AppError.ValidationError("No barcode/QR code could be found in the scanned image."))
        }

        val symbology = when (result.barcodeFormat) {
            BarcodeFormat.QR_CODE -> BarcodeSymbology.QR_CODE
            BarcodeFormat.CODE_128 -> BarcodeSymbology.CODE_128
            BarcodeFormat.EAN_13 -> BarcodeSymbology.EAN_13
            else -> BarcodeSymbology.UNKNOWN
        }
        val rawValue = result.text

        val exactMatch = candidateStockItems.firstOrNull { buildPayload(it) == rawValue }
        val looseMatch = exactMatch ?: candidateStockItems.firstOrNull {
            (it.sku.isNotBlank() && rawValue.contains(it.sku)) || rawValue.contains(it.itemId)
        }

        return AccountingResult.Success(
            BarcodeScanSuggestion(
                rawValue = rawValue,
                symbology = symbology,
                matchedStockItemId = looseMatch?.itemId,
                confidenceScore = if (exactMatch != null) 1.0 else if (looseMatch != null) 0.6 else 0.0
            )
        )
    }

    private fun buildPayload(stockItem: StockItem): String =
        if (stockItem.sku.isNotBlank()) "SKU:${stockItem.sku}|ID:${stockItem.itemId}" else "ID:${stockItem.itemId}"
}

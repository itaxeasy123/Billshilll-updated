package com.example.accounting.domain.rendering

/** [IMPORT_SOURCE_FILE]/[OCR_SOURCE_IMAGE] (Phase 7J UI) - a picked CSV/JSON file or a captured
 * receipt photo, persisted as a checksummed [DocumentAsset] before [com.example.accounting.domain.dataimport.DataImportAdapter.parseFile]/
 * [com.example.accounting.domain.ocr.OcrIngestionAdapter.extractFromDocument] ever reads it - the
 * same Draft/Suggestion/Review discipline every other asset type in this app already follows. */
enum class DocumentAssetType { LOGO, SIGNATURE, QR_CODE, IMPORT_SOURCE_FILE, OCR_SOURCE_IMAGE }

/**
 * A reference to one binary branding asset (Phase 7D, Section 8) - the accounting database never
 * stores the image bytes themselves, only [storageReference] (an app-private file path/URI) plus
 * enough metadata ([checksum], [mimeType], [sizeBytes]) to detect a stale or corrupted reference.
 * Company-scoped like every other Phase 7D entity.
 */
data class DocumentAsset(
    val assetId: String,
    val companyId: String,
    val type: DocumentAssetType,
    val storageReference: String,
    val checksum: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)

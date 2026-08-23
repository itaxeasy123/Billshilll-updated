package com.example.accounting.domain.rendering

enum class DocumentAssetType { LOGO, SIGNATURE, QR_CODE }

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

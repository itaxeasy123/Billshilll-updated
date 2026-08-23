package com.example.accounting.domain.ocr

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity
import com.example.accounting.domain.rendering.BusinessProfile
import java.time.LocalDate

/**
 * What kind of source document OCR extraction believes it's looking at - a guess to help a human
 * reviewer pick the right entry screen, never used to auto-select a posting path.
 */
enum class OcrDocumentType { PURCHASE_BILL, EXPENSE_RECEIPT, SALES_INVOICE, UNKNOWN }

/** One extracted line item, every field a *suggestion* the human reviewer confirms or corrects -
 * never written anywhere until they do. */
data class OcrLineItemSuggestion(
    val description: String,
    val quantity: Quantity? = null,
    val rate: Money? = null,
    val amount: Money,
    val hsnSacCode: String? = null,
    val gstRatePercent: Double? = null
)

/**
 * Result of running OCR extraction against an already-uploaded
 * [com.example.accounting.domain.rendering.DocumentAsset] (referenced by [sourceAssetId] - the
 * scanned image/PDF itself is never duplicated or re-modeled here). Every field is a best-effort
 * *guess* - [confidenceScore] makes that explicit - meant to pre-fill a voucher-entry screen for a
 * human to review, edit, and submit through the existing entry flow. Nothing in this type is ever
 * posted automatically.
 */
data class OcrExtractionResult(
    val sourceAssetId: String,
    val documentType: OcrDocumentType,
    val confidenceScore: Double,
    val vendorNameGuess: String? = null,
    val vendorGstinGuess: String? = null,
    val documentDateGuess: LocalDate? = null,
    val totalAmountGuess: Money? = null,
    val lineItems: List<OcrLineItemSuggestion> = emptyList()
)

/**
 * Adapter boundary for OCR/bill-scanning ingestion (Phase 7I) - a pure Kotlin interface only, no
 * implementation, no OCR/ML library dependency, no Android dependency. Mirrors
 * [com.example.accounting.domain.sandbox.SandboxProviderAdapter]'s shape exactly: an interface
 * plus minimal typed models, `AccountingResult<T>` return convention,
 * [com.example.accounting.domain.rendering.BusinessProfile] as the tenant/caller context.
 *
 * **This adapter can never create, edit, or post a [com.example.accounting.domain.accounting.Voucher]
 * or touch any [com.example.accounting.domain.accounting.Ledger] balance.** It only ever turns an
 * already-uploaded document image into a suggested, human-reviewable structure - the existing
 * voucher-entry path (`DoubleEntryValidator` -> `postVoucher`) is the only way anything it
 * produces ever reaches the books, and that always requires a human to review and submit it first.
 */
interface OcrIngestionAdapter {
    suspend fun extractFromDocument(
        requestingCompany: BusinessProfile,
        documentAssetId: String
    ): AccountingResult<OcrExtractionResult>
}

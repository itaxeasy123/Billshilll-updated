package com.example.accounting.application.numbering

/**
 * A configurable document/voucher numbering scheme (Phase 7J-A) - **strictly read-only domain
 * logic**: a data holder only, no generation/mutation function anywhere in this file. Today,
 * every company's number format is hardcoded on both sides: `AccountingRepository.generateNextVoucherNumber`
 * always produces `"${VoucherType.prefix}$yearPart-${4-digit-count}"` (e.g. `"SAL-2026-0001"`),
 * and `generateNextDocumentNumber` follows the identical hardcoded shape for Invoice/TradeDocument
 * numbers - neither is configurable per company, and neither is changed by this type existing.
 * [DocumentNumberConfig] is the shape a future, per-company-configurable numbering scheme would
 * need (e.g. `"INV/2026-27/00001"` instead of `"SAL-2026-0001"`), without touching either existing,
 * frozen function's current behavior.
 *
 * [documentTypeCode] deliberately holds a plain `String` (`VoucherType.code` or `DocumentType`'s
 * own code, e.g. `"SAL"`/`"PUR"`) rather than either enum directly - this config is meant to cover
 * both voucher and document/invoice numbering with one shape, and the two don't share a common
 * enum type.
 */
data class DocumentNumberConfig(
    val companyId: String,
    val documentTypeCode: String,
    val prefix: String,
    val suffix: String = "",
    val nextSequenceNumber: Long,
    val paddingSize: Int
) {
    /** Pure formatting - reads [nextSequenceNumber] as given, never advances it, never persists
     * anything. A future generator function (not defined here) would be the only thing that reads
     * the live sequence and advances it, atomically, exactly like `generateNextVoucherNumber`
     * already does today with its hardcoded format. */
    fun formatted(): String = "$prefix${nextSequenceNumber.toString().padStart(paddingSize, '0')}$suffix"
}

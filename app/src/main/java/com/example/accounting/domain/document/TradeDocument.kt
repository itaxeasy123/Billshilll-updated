package com.example.accounting.domain.document

import java.time.LocalDate

/**
 * A non-posting trade document (Phase 7B) - Quotation, Proforma Invoice, Sales/Purchase Order,
 * Delivery Note, or Receipt Note. Never creates any Ledger/JournalItem/Voucher row merely by
 * existing. [sourceTradeDocumentId] points at the TradeDocument this one was converted FROM
 * (nullable - most documents are created directly, with no source at all). "What did this convert
 * INTO" is answered by a reverse lookup (another TradeDocument, or an
 * [com.example.accounting.domain.invoice.Invoice], whose own sourceTradeDocumentId equals this
 * document's id) - never a forward-pointing field that would need to be kept in sync.
 */
data class TradeDocument(
    val tradeDocumentId: String,
    val companyId: String,
    val financialYearId: String,
    val documentType: DocumentType,
    /** Assigned by the repository at creation time via `generateNextDocumentNumber` - a caller
     * building a TradeDocument to pass to `createTradeDocument` doesn't know this yet. */
    val documentNumber: String? = null,
    val partyId: String,
    val date: LocalDate,
    val status: DocumentStatus = DocumentStatus.DRAFT,
    val sourceTradeDocumentId: String? = null,
    val narration: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

package com.example.accounting.domain.invoice

import java.time.LocalDate

enum class InvoiceType {
    SALES_INVOICE,
    PURCHASE_BILL,
    CREDIT_NOTE,
    DEBIT_NOTE
}

/**
 * A pre-posting, non-accounting-affecting document (Phase 7A) that becomes a real, immutable
 * [com.example.accounting.domain.accounting.Voucher] only once posted through
 * [com.example.accounting.data.repository.AccountingRepository.postInvoice] - the frozen
 * `VoucherPostingEngine` has no "draft" concept, so this is a genuinely separate table, not a new
 * Voucher state. Before [voucherId] is set, an Invoice has zero Ledger/JournalItem/Trial-Balance/
 * P&L effect, identical to how the existing non-posting VoucherTypes (Quotation, Proforma) behave.
 *
 * [invoiceNumber] (Phase 7B) is assigned at DRAFT-creation time via the new
 * `generateNextDocumentNumber` - deliberately its OWN independent sequence, never assumed to equal
 * the linked Voucher's own `voucherNumber` (generated separately by the existing, unchanged
 * `generateNextVoucherNumber`). Posting only ever sets [voucherId]; it never touches
 * [invoiceNumber] again.
 */
data class Invoice(
    val invoiceId: String,
    val companyId: String,
    val financialYearId: String,
    val invoiceType: InvoiceType,
    val invoiceNumber: String? = null,
    val partyId: String,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val voucherId: String? = null,
    /** Credit Note -> original Sales Invoice's invoiceId, Debit Note -> original Purchase Bill's
     * invoiceId. Null otherwise. Mirrors [com.example.accounting.domain.accounting.Voucher.referenceVoucherId]. */
    val referenceInvoiceId: String? = null,
    /** Phase 7B - the [com.example.accounting.domain.document.TradeDocument] this Invoice was
     * converted from (e.g. a Sales Order becoming a Sales Invoice). Null when created directly -
     * a user is never required to start from a Quotation/Order. */
    val sourceTradeDocumentId: String? = null,
    val narration: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

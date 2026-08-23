package com.example.accounting.domain.document

/**
 * User-facing document identity (Phase 7B) - deliberately decoupled from
 * [com.example.accounting.domain.accounting.VoucherType]'s own numbering. A posting document's
 * accounting Voucher number (e.g. "INV-2026-0001", from the existing, unchanged
 * `generateNextVoucherNumber`) and its own document number (e.g. "SI-2026-0001", from the new
 * `generateNextDocumentNumber`) are two independent identifiers by design - see
 * [com.example.accounting.domain.invoice.Invoice.invoiceNumber].
 *
 * The 6 non-posting prefixes reuse [com.example.accounting.domain.accounting.VoucherType]'s
 * existing values verbatim (established in Phase 5, never used for an actual number until now);
 * the 4 posting-document prefixes are new and intentionally distinct from the Voucher's own.
 */
enum class DocumentType(val prefix: String, val isPostingDocument: Boolean) {
    SALES_INVOICE("SI-", true),
    PURCHASE_BILL("PB-", true),
    CREDIT_NOTE("CN-", true),
    DEBIT_NOTE("DN-", true),
    QUOTATION("EST-", false),
    PROFORMA_INVOICE("PI-", false),
    SALES_ORDER("SO-", false),
    PURCHASE_ORDER("PO-", false),
    DELIVERY_NOTE("DC-", false),
    RECEIPT_NOTE("GRN-", false)
}

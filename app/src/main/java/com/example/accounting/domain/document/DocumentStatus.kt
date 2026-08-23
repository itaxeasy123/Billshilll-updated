package com.example.accounting.domain.document

/**
 * Lifecycle for non-posting [TradeDocument]s only (Phase 7B) - unlike
 * [com.example.accounting.domain.invoice.InvoiceStatus] (always derived from accounting data,
 * never stored), this is a genuine stored field: a TradeDocument never touches accounting, so
 * there is no risk of drifting from ledger reality the way a settable Invoice status would.
 *
 * DRAFT: freely editable (lines can change).
 * ISSUED: lines become immutable.
 * CONVERTED: set automatically once another document/invoice references this one as its source;
 *            a CONVERTED document can never be cancelled directly.
 * CANCELLED: terminal. A DRAFT is hard-deleted (never shown to anyone); an ISSUED document is
 *            marked CANCELLED instead (record preserved).
 */
enum class DocumentStatus {
    DRAFT,
    ISSUED,
    CONVERTED,
    CANCELLED
}

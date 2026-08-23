package com.example.accounting.domain.rendering

/**
 * Common renderer boundary (Phase 7D, Section 14): a renderer only ever turns an already-assembled
 * [DocumentData] + [DocumentTemplate] into an output artifact - it must never read Voucher/
 * JournalItem/GstTransaction rows itself, and it must never import an accounting/GST calculation
 * type. [JsonDocumentRenderer] is the pure (no Android dependency) implementation; the PDF/Print/
 * Share adapters necessarily need an Android `Context` for file I/O and therefore live in the
 * `data`/`core` layer instead of implementing this exact interface shape (see
 * `docs/44_PDF_PRINT_SHARE.md`) - the architectural invariant (render-only, never recalculate) is
 * what's shared, not a single Kotlin type.
 */
interface DocumentRenderer<T> {
    fun render(data: DocumentData, template: DocumentTemplate): T
}

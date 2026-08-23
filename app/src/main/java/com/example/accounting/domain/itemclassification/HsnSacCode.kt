package com.example.accounting.domain.itemclassification

/**
 * A structured HSN (goods) / SAC (services) classification record (Phase 7J) - the first real
 * type this codebase has ever had for this. Before this phase, HSN/SAC only ever existed as a
 * free-text `hsnSacCode: String` field scattered across several frozen entities
 * (`StockItem`/`InvoiceLine`/`TradeDocumentLine`/`GstTransaction`) with no structured backing -
 * the Phase 7E export audit specifically flagged this as why
 * `GSTTransactionExportDto.isService` could only ever be `null`: no domain model anywhere stored a
 * goods-vs-service classification. [isService] here is exactly that missing fact, finally given a
 * real type - though wiring it back into those frozen entities/exports is left for a future
 * phase, not done here (this phase adds new files only, per the "never touch a frozen file"
 * discipline held throughout 7H-7J).
 *
 * **Deliberately carries no GST rate field.** Unlike a profession (which never had a rate to
 * begin with), an HSN/SAC code's real-world applicable GST rate genuinely does vary by government
 * notification over time - baking a rate onto this record would create a second, driftable source
 * of tax truth alongside the existing, frozen `GstTransaction.gstRatePercent`/`GstCalculationEngine`,
 * which already correctly treats the rate as a per-transaction fact the user enters/verifies, not
 * a lookup. This type is classification only: what a code means, never what it costs.
 */
data class HsnSacCode(
    val code: String,
    val description: String,
    val isService: Boolean
)

/** A handful of illustrative entries - not a real government HSN/SAC master list (which runs to
 * thousands of codes and is explicitly out of scope for an architecture-only phase). A real
 * catalog is a future data-loading concern, not something to hardcode here. */
object IllustrativeHsnSacCodes {
    val TEXTILE_FABRIC = HsnSacCode("5208", "Cotton fabric, woven", isService = false)
    val GOLD_JEWELLERY = HsnSacCode("7113", "Articles of jewellery of precious metal", isService = false)
    val ACCOUNTING_SERVICES = HsnSacCode("998222", "Accounting and bookkeeping services", isService = true)
    val LEGAL_SERVICES = HsnSacCode("998211", "Legal advisory and representation services", isService = true)

    val ALL: List<HsnSacCode> = listOf(TEXTILE_FABRIC, GOLD_JEWELLERY, ACCOUNTING_SERVICES, LEGAL_SERVICES)
}

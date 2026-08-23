package com.example.accounting.domain.profession

/** A coarse grouping [BusinessProfession] values fall into - useful for future GST/ITR context
 * resolution (e.g. "which ITR form category") without hardcoding a rule per individual
 * profession. */
enum class BusinessProfessionCategory { RETAIL, WHOLESALE, MANUFACTURING, PROFESSIONAL_SERVICES, TRADE_CONTRACTOR, OTHER }

/**
 * A business/profession classification (Phase 7J) - deliberately a plain data record, not a
 * closed `enum class`, precisely so a new profession can be added by constructing a new value
 * with a fresh [code] anywhere a caller needs one, without ever touching this file or any code
 * that already consumes [BusinessProfession] - that's what "must be extensible" means
 * structurally, not just as a comment. [StandardBusinessProfessions] lists the illustrative set
 * named when this phase was scoped (Retailer, Wholesaler, Doctor, Engineer, Goldsmith,
 * Contractor); it is a starting catalog, not an exhaustive or closed one.
 *
 * **Deliberately carries no GST rate, tax percentage, or any monetary field** - a profession is
 * context for classification (which future GST/ITR module a company's activity relates to), never
 * a source of truth for a tax rate. GST rates are transaction-level facts entered/verified per
 * voucher (see the existing, frozen `GstCalculationEngine`/`GstTransaction.gstRatePercent`), never
 * derived from what profession a company practices - a Goldsmith's sale of a specific item is
 * taxed by that item's actual GST rate, never by an assumption baked into "Goldsmith" as a label.
 *
 * How this eventually attaches to [com.example.accounting.domain.company.Company] or
 * [com.example.accounting.domain.rendering.BusinessProfile] is an open question left for whichever
 * future phase actually wires GST/ITR context resolution - not resolved here, to avoid touching
 * either of those frozen types speculatively (the same discipline `docs/45_DOCUMENT_BRANDING.md`
 * already used for its own deliberately-unresolved "7D vs 7G" boundary question).
 */
data class BusinessProfession(
    val code: String,
    val displayName: String,
    val category: BusinessProfessionCategory
)

/** Illustrative starting catalog - not exhaustive, not closed. A future phase may add more
 * without touching [BusinessProfession] itself. */
object StandardBusinessProfessions {
    val RETAILER = BusinessProfession("RETAILER", "Retailer", BusinessProfessionCategory.RETAIL)
    val WHOLESALER = BusinessProfession("WHOLESALER", "Wholesaler", BusinessProfessionCategory.WHOLESALE)
    val DOCTOR = BusinessProfession("DOCTOR", "Doctor", BusinessProfessionCategory.PROFESSIONAL_SERVICES)
    val ENGINEER = BusinessProfession("ENGINEER", "Engineer", BusinessProfessionCategory.PROFESSIONAL_SERVICES)
    val GOLDSMITH = BusinessProfession("GOLDSMITH", "Goldsmith", BusinessProfessionCategory.MANUFACTURING)
    val CONTRACTOR = BusinessProfession("CONTRACTOR", "Contractor", BusinessProfessionCategory.TRADE_CONTRACTOR)

    val ALL: List<BusinessProfession> = listOf(RETAILER, WHOLESALER, DOCTOR, ENGINEER, GOLDSMITH, CONTRACTOR)
}

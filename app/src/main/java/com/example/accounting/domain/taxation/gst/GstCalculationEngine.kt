package com.example.accounting.domain.taxation.gst

import com.example.accounting.core.common.Money

/**
 * Standard IDs for the system-provisioned GST duty ledgers (see
 * `AccountingRepository.seedInitialDataForCompany`/`ensureGstLedgersExist`). Centralized the same
 * way `StandardSystemGroups` centralizes group IDs, so tax-ledger resolution is exact-ID based,
 * never a `ledgerName.contains("Output CGST", ...)` guess.
 *
 * Output (Sales, outward supply) and Input (Purchase, inward supply) are DISTINCT ledgers per tax
 * type - six ledgers total, all under the "Duties & Taxes" group - never one ledger conflating
 * both directions, per Phase 4.5 Section B.1. CGST/SGST/IGST are likewise kept as separate ledger
 * identities because their balances and statutory reporting differ.
 */
object GstLedgerIds {
    const val OUTPUT_CGST_LEDGER_ID = "LED_GST_OUTPUT_CGST"
    const val OUTPUT_SGST_LEDGER_ID = "LED_GST_OUTPUT_SGST"
    const val OUTPUT_IGST_LEDGER_ID = "LED_GST_OUTPUT_IGST"
    const val INPUT_CGST_LEDGER_ID = "LED_GST_INPUT_CGST"
    const val INPUT_SGST_LEDGER_ID = "LED_GST_INPUT_SGST"
    const val INPUT_IGST_LEDGER_ID = "LED_GST_INPUT_IGST"
    /** One CESS ledger, not split Output/Input - CESS is levied one-directionally per item
     * (Phase 5, Priority 5), unlike CGST/SGST/IGST which always have a reciprocal Input/Output pair. */
    const val CESS_LEDGER_ID = "LED_GST_CESS"

    /** All seven, for seeding/backfill iteration. */
    val ALL_BARE_IDS = listOf(
        OUTPUT_CGST_LEDGER_ID, OUTPUT_SGST_LEDGER_ID, OUTPUT_IGST_LEDGER_ID,
        INPUT_CGST_LEDGER_ID, INPUT_SGST_LEDGER_ID, INPUT_IGST_LEDGER_ID,
        CESS_LEDGER_ID
    )
}

/** Legal nature of a supply (Phase 5, Priority 3) - distinct from [SupplyType], which is purely a
 * geography fact (intra vs inter-state). A NORMAL-nature supply still resolves to INTRA/INTER_STATE
 * from [GstTransactionFacts.supplierStateCode]/[GstTransactionFacts.placeOfSupply]; EXPORT and
 * EXEMPT/NIL_RATED bypass that geography resolution entirely (zero tax regardless of state). */
enum class GstSupplyNature { NORMAL, EXPORT, EXEMPT, NIL_RATED }

/**
 * The full fact set for a single GST-bearing line (Phase 5, Priority 3) - "Place of Supply" is
 * kept distinct from the party's registered/billing state because GST law taxes based on where the
 * supply is deemed to occur, not necessarily the buyer's regular address.
 */
data class GstTransactionFacts(
    val taxableAmount: Money,
    val gstRatePercent: Double,
    val cessRatePercent: Double = 0.0,
    val supplierStateCode: String,
    val placeOfSupply: String,
    val supplyNature: GstSupplyNature = GstSupplyNature.NORMAL
)

/** Which direction a GST-bearing transaction posts to - Sales is Output, Purchase is Input. */
enum class GstDirection { OUTPUT, INPUT }

/**
 * Engine-side GST computation (Section "GST calculation must be engine-side" of the Phase 4
 * spec) - wraps [GSTRules]' pure math with the intra/inter-state resolution step, so callers
 * (repository/ViewModel) never compute tax splits themselves; they only supply quantity x rate
 * (the taxable value) and read the result. Deliberately NOT merged into
 * [com.example.accounting.domain.inventory.engine.InventoryEngine] - inventory answers "how many
 * units, at what valuation," GST answers "what taxable value, CGST/SGST or IGST," and posting
 * answers "which ledgers" - three separate concerns.
 */
object GstCalculationEngine {

    fun calculate(
        taxableAmount: Money,
        taxRatePercent: Double,
        companyStateCode: String,
        partyStateCode: String
    ): TaxBreakdown {
        val supplyType = GSTRules.determineSupplyType(companyStateCode, partyStateCode)
        return GSTRules.calculateTax(taxableAmount, taxRatePercent, supplyType)
    }

    /**
     * Same as [calculate], but when [partyStateCode] is unavailable (no state code on file for
     * the party ledger yet), falls back to an explicitly-selected [fallbackInterState] flag
     * instead of guessing - callers pass whatever the UI's Intra/Inter-State toggle currently
     * holds. Place of Supply from real state data always wins when it's available.
     */
    fun calculateWithFallback(
        taxableAmount: Money,
        taxRatePercent: Double,
        companyStateCode: String,
        partyStateCode: String,
        fallbackInterState: Boolean
    ): TaxBreakdown {
        val supplyType = if (partyStateCode.isNotBlank()) {
            GSTRules.determineSupplyType(companyStateCode, partyStateCode)
        } else if (fallbackInterState) SupplyType.INTER_STATE else SupplyType.INTRA_STATE
        return GSTRules.calculateTax(taxableAmount, taxRatePercent, supplyType)
    }

    /**
     * The richer entry point used by [com.example.accounting.domain.trading.TradingWorkflowEngine]
     * (Phase 5, Priority 1/3) - resolves supply type from [GstTransactionFacts.supplyNature] first
     * (EXPORT/EXEMPT/NIL_RATED bypass geography entirely, making [SupplyType.EXPORT]/[SupplyType.EXEMPT]
     * actually reachable, unlike [calculate]/[calculateWithFallback] which only ever produce
     * INTRA_STATE/INTER_STATE), then adds CESS on top of [GSTRules.calculateTax]'s CGST/SGST/IGST
     * split. Left as a separate function - [calculate]/[calculateWithFallback] are unchanged so
     * every existing call site (Phase 4.5's Sales/Purchase GST voucher flow, and the `h1`-`h3`/`j5`-`j6`
     * tests that exercise them directly) keeps compiling and behaving identically.
     */
    fun calculateDetailed(facts: GstTransactionFacts): TaxBreakdown {
        val supplyType = when (facts.supplyNature) {
            GstSupplyNature.EXPORT -> SupplyType.EXPORT
            GstSupplyNature.EXEMPT, GstSupplyNature.NIL_RATED -> SupplyType.EXEMPT
            GstSupplyNature.NORMAL -> GSTRules.determineSupplyType(facts.supplierStateCode, facts.placeOfSupply)
        }
        val breakdown = GSTRules.calculateTax(facts.taxableAmount, facts.gstRatePercent, supplyType)
        val cess = if (facts.cessRatePercent > 0.0) facts.taxableAmount.percentage(facts.cessRatePercent) else Money.ZERO
        return breakdown.copy(
            cessRatePercent = facts.cessRatePercent,
            cessAmount = cess,
            totalTax = breakdown.totalTax + cess,
            totalWithTax = breakdown.totalWithTax + cess
        )
    }
}

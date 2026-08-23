package com.example.accounting.domain.accounting

/**
 * Formal Voucher & Document Classification as specified in Master Requirement 17:
 * 1. FINANCIAL_POSTING: Directly generates double-entry debits/credits in the general ledger.
 * 2. INVENTORY_ONLY: Updates physical inventory quantities/locations without financial ledger impact.
 * 3. NON_POSTING: Pure document commitments (Quotations, Proforma Invoices, Sales Orders) with zero ledger/stock impact.
 * 4. CONDITIONAL_POSTING: Provisional/optional documents that post only upon explicit approval or status transition.
 */
enum class DocumentPostingType(
    val code: String,
    val displayName: String,
    val description: String,
    val generatesJournalEntries: Boolean,
    val impactsInventoryBalances: Boolean
) {
    FINANCIAL_POSTING(
        code = "FIN_POST",
        displayName = "Financial Posting",
        description = "Creates atomic double-entry debit and credit journal items updating general ledger balances",
        generatesJournalEntries = true,
        impactsInventoryBalances = false
    ),
    INVENTORY_ONLY(
        code = "INV_ONLY",
        displayName = "Inventory Movement Only",
        description = "Records physical stock receipt/dispatch (e.g. Delivery Challan, GRN) without financial impact",
        generatesJournalEntries = false,
        impactsInventoryBalances = true
    ),
    NON_POSTING(
        code = "NON_POST",
        displayName = "Non-Posting Document",
        description = "Commercial commitments (Quotations, Proforma Invoices, Sales Orders) prior to execution",
        generatesJournalEntries = false,
        impactsInventoryBalances = false
    ),
    CONDITIONAL_POSTING(
        code = "COND_POST",
        displayName = "Conditional / Provisional Posting",
        description = "Draft or optional transactions that require secondary authorization before ledger posting",
        generatesJournalEntries = false,
        impactsInventoryBalances = false
    );

    val isFinancial: Boolean get() = generatesJournalEntries
    val isNonPosting: Boolean get() = !generatesJournalEntries && !impactsInventoryBalances
}

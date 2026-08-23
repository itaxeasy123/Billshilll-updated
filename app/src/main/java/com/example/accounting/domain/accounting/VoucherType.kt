package com.example.accounting.domain.accounting

/**
 * Production-grade Voucher Type definitions.
 * Categorized into:
 * 1. Financial Posting Transactions (Double-entry journal posting)
 * 2. Non-Posting / Order & Movement Documents
 */
enum class VoucherType(
    val code: String,
    val displayName: String,
    val prefix: String,
    val description: String,
    val isDocumentOnly: Boolean = false,
    val postsAccounting: Boolean = true,
    val affectsInventory: Boolean = false,
    val createsGst: Boolean = false,
    val requiresParty: Boolean = false,
    val requiresLedgerLines: Boolean = true,
    val canBeReversed: Boolean = true,
    val canBeDeleted: Boolean = true,
    val requiresApproval: Boolean = false
) {
    // -------------------------------------------------------------
    // TRANSACTION TYPES (POSTING TO ACCOUNTING LEDGERS)
    // -------------------------------------------------------------
    PAYMENT(
        code = "PMT",
        displayName = "Payment",
        prefix = "PMT-",
        description = "Disbursements from Cash/Bank to vendors, expenses, or assets",
        postsAccounting = true,
        affectsInventory = false,
        createsGst = false,
        requiresParty = false,
        requiresLedgerLines = true,
        canBeReversed = true,
        canBeDeleted = true
    ),
    RECEIPT(
        code = "RCT",
        displayName = "Receipt",
        prefix = "RCT-",
        description = "Money inflows into Cash/Bank from debtors, income, or capital",
        postsAccounting = true,
        affectsInventory = false,
        createsGst = false,
        requiresParty = false,
        requiresLedgerLines = true,
        canBeReversed = true,
        canBeDeleted = true
    ),
    CONTRA(
        code = "CTR",
        displayName = "Contra",
        prefix = "CTR-",
        description = "Internal funds transfers between Cash and Bank accounts",
        postsAccounting = true,
        affectsInventory = false,
        createsGst = false,
        requiresParty = false,
        requiresLedgerLines = true,
        canBeReversed = true,
        canBeDeleted = true
    ),
    SALES(
        code = "SAL",
        displayName = "Sales Invoice",
        prefix = "INV-",
        description = "Tax invoice for sales of goods or services to customers",
        postsAccounting = true,
        affectsInventory = true,
        createsGst = true,
        requiresParty = true,
        requiresLedgerLines = true,
        canBeReversed = true,
        canBeDeleted = true
    ),
    PURCHASE(
        code = "PUR",
        displayName = "Purchase Bill",
        prefix = "PUR-",
        description = "Inward purchase bills from suppliers or service providers",
        postsAccounting = true,
        affectsInventory = true,
        createsGst = true,
        requiresParty = true,
        requiresLedgerLines = true,
        canBeReversed = true,
        canBeDeleted = true
    ),
    JOURNAL(
        code = "JRN",
        displayName = "Journal",
        prefix = "JRN-",
        description = "General adjustments, depreciation, opening entries, accruals",
        postsAccounting = true,
        affectsInventory = false,
        createsGst = false,
        requiresParty = false,
        requiresLedgerLines = true,
        canBeReversed = true,
        canBeDeleted = true
    ),
    DEBIT_NOTE(
        code = "DRN",
        displayName = "Debit Note",
        prefix = "DRN-",
        description = "Purchase returns, escalation in vendor prices",
        postsAccounting = true,
        affectsInventory = true,
        createsGst = true,
        requiresParty = true,
        requiresLedgerLines = true,
        canBeReversed = true,
        canBeDeleted = true
    ),
    CREDIT_NOTE(
        code = "CRN",
        displayName = "Credit Note",
        prefix = "CRN-",
        description = "Sales returns, trade discounts, rate deductions",
        postsAccounting = true,
        affectsInventory = true,
        createsGst = true,
        requiresParty = true,
        requiresLedgerLines = true,
        canBeReversed = true,
        canBeDeleted = true
    ),
    REVERSING_JOURNAL(
        code = "RVJ",
        displayName = "Reversing Journal",
        prefix = "RVJ-",
        description = "Provisional journal with automated reversal at future date",
        postsAccounting = true,
        affectsInventory = false,
        createsGst = false,
        requiresParty = false,
        requiresLedgerLines = true,
        canBeReversed = true,
        canBeDeleted = true
    ),
    STOCK_JOURNAL(
        code = "STJ",
        displayName = "Stock Journal",
        prefix = "STJ-",
        description = "Inter-warehouse transfers, manufacturing consumption, inventory revaluation",
        postsAccounting = true,
        affectsInventory = true,
        createsGst = false,
        requiresParty = false,
        requiresLedgerLines = false,
        canBeReversed = true,
        canBeDeleted = true
    ),

    // -------------------------------------------------------------
    // NON-POSTING / DOCUMENT TYPES (ORDERS & DISPATCH)
    // -------------------------------------------------------------
    SALES_ORDER(
        code = "SO",
        displayName = "Sales Order",
        prefix = "SO-",
        description = "Customer order commitments prior to invoice/dispatch",
        isDocumentOnly = true,
        postsAccounting = false,
        affectsInventory = false,
        createsGst = false,
        requiresParty = true,
        requiresLedgerLines = false,
        canBeReversed = false,
        canBeDeleted = true
    ),
    PURCHASE_ORDER(
        code = "PO",
        displayName = "Purchase Order",
        prefix = "PO-",
        description = "Vendor purchase order commitments prior to receipt/bill",
        isDocumentOnly = true,
        postsAccounting = false,
        affectsInventory = false,
        createsGst = false,
        requiresParty = true,
        requiresLedgerLines = false,
        canBeReversed = false,
        canBeDeleted = true
    ),
    DELIVERY_NOTE(
        code = "DN",
        displayName = "Delivery Note (Challan)",
        prefix = "DC-",
        description = "Goods delivery challan dispatched to customer",
        isDocumentOnly = true,
        postsAccounting = false,
        affectsInventory = true,
        createsGst = false,
        requiresParty = true,
        requiresLedgerLines = false,
        canBeReversed = false,
        canBeDeleted = true
    ),
    RECEIPT_NOTE(
        code = "RN",
        displayName = "Receipt Note (GRN)",
        prefix = "GRN-",
        description = "Goods receipt note received into warehouse from vendor",
        isDocumentOnly = true,
        postsAccounting = false,
        affectsInventory = true,
        createsGst = false,
        requiresParty = true,
        requiresLedgerLines = false,
        canBeReversed = false,
        canBeDeleted = true
    ),
    MEMORANDUM(
        code = "MEM",
        displayName = "Memorandum",
        prefix = "MEM-",
        description = "Pure reminder / informational record with no ledger or stock impact",
        isDocumentOnly = true,
        postsAccounting = false,
        affectsInventory = false,
        createsGst = false,
        requiresParty = false,
        requiresLedgerLines = false,
        canBeReversed = false,
        canBeDeleted = true
    ),
    QUOTATION(
        code = "QUO",
        displayName = "Quotation",
        prefix = "EST-",
        description = "Price quotation / commercial estimate provided to prospect (Non-posting)",
        isDocumentOnly = true,
        postsAccounting = false,
        affectsInventory = false,
        createsGst = false,
        requiresParty = true,
        requiresLedgerLines = false,
        canBeReversed = false,
        canBeDeleted = true
    ),
    PROFORMA_INVOICE(
        code = "PI",
        displayName = "Proforma Invoice",
        prefix = "PI-",
        description = "Preliminary sales bill requested prior to goods delivery/payment (Non-posting)",
        isDocumentOnly = true,
        postsAccounting = false,
        affectsInventory = false,
        createsGst = false,
        requiresParty = true,
        requiresLedgerLines = false,
        canBeReversed = false,
        canBeDeleted = true
    );

    val postingCategory: DocumentPostingType
        get() = when {
            postsAccounting -> DocumentPostingType.FINANCIAL_POSTING
            affectsInventory -> DocumentPostingType.INVENTORY_ONLY
            else -> DocumentPostingType.NON_POSTING
        }

    companion object {
        fun fromCode(code: String): VoucherType {
            return entries.find { it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) } ?: JOURNAL
        }
    }
}

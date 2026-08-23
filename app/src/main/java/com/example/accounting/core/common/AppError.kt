package com.example.accounting.core.common

sealed class AppError(open val message: String) {
    data class ValidationError(override val message: String) : AppError(message)
    data class TenantMismatch(val companyId: String, val resourceCompanyId: String) : 
        AppError("Tenant mismatch: Request for company $companyId cannot access resource belonging to $resourceCompanyId")
    data class PeriodLocked(val periodName: String, val date: String) : 
        AppError("Accounting period '$periodName' is locked for date $date. No accounting transactions may be posted or altered.")
    data class UnbalancedVoucher(val totalDebits: Money, val totalCredits: Money, val difference: Money) : 
        AppError("Double-entry violation: Total debits (${totalDebits.format()}) must strictly equal total credits (${totalCredits.format()}). Difference: ${difference.format()}")
    data class LedgerNotFound(val ledgerId: String) : 
        AppError("Ledger with ID '$ledgerId' was not found in active company Chart of Accounts.")
    data class InsufficientBalance(val ledgerName: String, val currentBalance: Money, val requiredAmount: Money) : 
        AppError("Ledger '$ledgerName' has insufficient balance (${currentBalance.format()}) to process debit of ${requiredAmount.format()}")
    data class DuplicateVoucherNumber(val voucherNumber: String, val financialYear: String) : 
        AppError("Voucher number '$voucherNumber' already exists for $financialYear.")
    data class InvalidGSTIN(val gstin: String) : 
        AppError("Invalid GSTIN format: '$gstin'. Expected 15-character alphanumeric format (e.g., 27AABCB1234D1ZM).")
    data class BusinessRuleViolation(override val message: String) : AppError(message)
    data class SystemError(override val message: String) : AppError(message)
    data class SyncError(override val message: String, val cause: Throwable? = null) : AppError(message)
    data class Unauthorized(val action: String, val role: String) : 
        AppError("User role '$role' is not authorized to perform action '$action'.")
    data class DatabaseError(override val message: String, val cause: Throwable? = null) : AppError(message)

    // ==================== FINANCIAL STATEMENTS (Phase 3) ====================
    data class TrialBalanceNotBalanced(val totalDebit: Money, val totalCredit: Money, val difference: Money) :
        AppError("TRIAL_BALANCE_NOT_BALANCED: Total debit (${totalDebit.format()}) does not equal total credit (${totalCredit.format()}). Difference: ${difference.format()}")
    data class BalanceSheetNotBalanced(val totalAssets: Money, val totalLiabilitiesAndEquity: Money, val difference: Money) :
        AppError("BALANCE_SHEET_NOT_BALANCED: Total Assets (${totalAssets.format()}) does not equal Total Liabilities + Equity (${totalLiabilitiesAndEquity.format()}). Difference: ${difference.format()}")
    data class InvalidFinancialYear(override val message: String) : AppError("INVALID_FINANCIAL_YEAR: $message")
    data class InvalidDateRange(override val message: String) : AppError("INVALID_DATE_RANGE: $message")
    data class GroupHierarchyInvalid(override val message: String) : AppError("GROUP_HIERARCHY_INVALID: $message")
    data class AccountingDataCorrupted(override val message: String) : AppError("ACCOUNTING_DATA_CORRUPTED: $message")

    // ==================== INVENTORY (Phase 4) ====================
    data class InsufficientStock(val itemName: String, val availableQuantity: String, val requestedQuantity: String) :
        AppError("INSUFFICIENT_STOCK: Item '$itemName' has $availableQuantity available, but $requestedQuantity was requested.")
    data class InvalidStockQuantity(override val message: String) : AppError("INVALID_STOCK_QUANTITY: $message")
    data class StockItemNotFound(val itemId: String) : AppError("STOCK_ITEM_NOT_FOUND: Stock item '$itemId' was not found in the active company.")

    // ==================== GST & STATUTORY (Phase 5) ====================
    data class InvalidContraLedger(val ledgerName: String) :
        AppError("INVALID_CONTRA_LEDGER: '$ledgerName' is not a Cash or Bank account. Contra vouchers may only move funds between Cash/Bank accounts.")
    data class InvalidAllocation(override val message: String) : AppError("INVALID_ALLOCATION: $message")
    data class OriginalVoucherNotFound(val voucherId: String) :
        AppError("ORIGINAL_VOUCHER_NOT_FOUND: Referenced voucher '$voucherId' was not found in the active company.")
    data class NoteExceedsOriginal(val noteAmount: Money, val originalAmount: Money) :
        AppError("NOTE_EXCEEDS_ORIGINAL: Note amount (${noteAmount.format()}) exceeds the original voucher amount (${originalAmount.format()}).")

    // ==================== EXPORT ARCHITECTURE (Phase 7E) ====================
    data class ExportFormatUnsupported(val format: String, val exportType: String) :
        AppError("EXPORT_FORMAT_UNSUPPORTED: Format '$format' is not supported for export type '$exportType'.")
    data class ExportSchemaUnsupported(val schemaVersion: String, val exportType: String) :
        AppError("EXPORT_SCHEMA_UNSUPPORTED: Schema version '$schemaVersion' is not supported for export type '$exportType'.")
    data class ResourceNotFound(val resourceType: String, val resourceId: String) :
        AppError("RESOURCE_NOT_FOUND: $resourceType '$resourceId' was not found.")
}

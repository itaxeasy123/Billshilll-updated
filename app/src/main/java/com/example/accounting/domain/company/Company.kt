package com.example.accounting.domain.company

enum class CompanyStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    ARCHIVED
}

/**
 * Production-grade Company entity acting as the root multi-tenant security boundary.
 * All financial data, ledgers, vouchers, financial years, and audit logs are strictly scoped by companyId.
 */
data class Company(
    val companyId: String,
    val name: String,
    val legalName: String = name,
    val tradeName: String = "",
    val cin: String = "",
    val gstin: String = "",
    val pan: String = "",
    val stateCode: String = "27",
    val stateName: String = "Maharashtra",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val currency: String = "INR",
    val financialYearStartMonth: Int = 4, // April (Indian FY)
    val accountingMode: AccountingMode = AccountingMode.ACCOUNT_ONLY,
    val businessType: BusinessType = BusinessType.TRADING,
    /**
     * Whether this company uses/enables GST functionality at all - a company-level
     * configuration/capability toggle, the same shape as [accountingMode]/[businessType], and
     * deliberately independent of [com.example.accounting.domain.accounting.Ledger.gstRegistrationStatus]
     * (a per-ledger *fact*, never inferred from or synced with this setting).
     *
     * Defaults to `true`, not `false`: every existing Sale/Purchase/Credit-Debit-Note voucher is
     * built with `Voucher.isGstApplicable = true` today regardless of any company setting (there
     * was none before this field), and GST amounts are always computed per line via
     * `GstCalculationEngine`. `true` is the only default that reproduces every existing company's
     * current behavior unchanged - `false` would silently flip every pre-existing company to a
     * "GST disabled" state nothing about their actual data or configuration ever chose.
     */
    val gstEnabled: Boolean = true,
    val status: CompanyStatus = CompanyStatus.ACTIVE,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(companyId.isNotBlank()) { "Company ID must not be blank" }
        require(name.isNotBlank()) { "Company name must not be blank" }
    }

    val isActive: Boolean get() = status == CompanyStatus.ACTIVE
}

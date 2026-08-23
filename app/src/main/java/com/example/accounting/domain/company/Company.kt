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

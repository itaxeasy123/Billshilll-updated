package com.example.accounting.domain.accounting

/**
 * Branch / Division organizational dimension model.
 * Branches are organizational units under a company and are distinct from accounting groups.
 */
data class Branch(
    val branchId: String,
    val companyId: String,
    val code: String,
    val name: String,
    val gstin: String? = null,
    val stateCode: String? = null,
    val address: String? = null,
    val isHeadOffice: Boolean = false,
    val isActive: Boolean = true
)

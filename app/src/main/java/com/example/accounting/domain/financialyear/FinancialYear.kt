package com.example.accounting.domain.financialyear

import java.time.LocalDate

enum class FinancialYearStatus {
    OPEN,
    CLOSED,
    AUDIT_LOCKED
}

enum class PeriodStatus {
    OPEN,
    LOCKED,
    AUDIT_LOCKED
}

/**
 * Domain entity representing an Indian Financial Year (1 April -> 31 March).
 */
data class FinancialYear(
    val financialYearId: String,
    val companyId: String,
    val fyCode: String, // e.g. "2026-27"
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: FinancialYearStatus = FinancialYearStatus.OPEN,
    val isCurrent: Boolean = false,
    val isLocked: Boolean = status != FinancialYearStatus.OPEN,
    val lockedAt: Long? = null,
    val lockedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(financialYearId.isNotBlank()) { "Financial Year ID must not be blank" }
        require(companyId.isNotBlank()) { "Company ID must not be blank" }
        require(startDate.isBefore(endDate)) { "Financial Year startDate ($startDate) must be before endDate ($endDate)" }
    }

    /**
     * Checks if a date falls strictly within this financial year.
     */
    fun contains(date: LocalDate): Boolean {
        return !date.isBefore(startDate) && !date.isAfter(endDate)
    }

    /**
     * Validates whether this represents a standard Indian Financial Year (1 April of Year N to 31 March of Year N+1).
     */
    fun isStandardIndianFinancialYear(): Boolean {
        return startDate.monthValue == 4 &&
                startDate.dayOfMonth == 1 &&
                endDate.monthValue == 3 &&
                endDate.dayOfMonth == 31 &&
                endDate.year == startDate.year + 1
    }

    companion object {
        fun createIndianFY(
            financialYearId: String,
            companyId: String,
            startYear: Int,
            isCurrent: Boolean = false
        ): FinancialYear {
            val start = LocalDate.of(startYear, 4, 1)
            val end = LocalDate.of(startYear + 1, 3, 31)
            val code = "$startYear-${(startYear + 1) % 100}"
            return FinancialYear(
                financialYearId = financialYearId,
                companyId = companyId,
                fyCode = code,
                startDate = start,
                endDate = end,
                isCurrent = isCurrent
            )
        }
    }
}

/**
 * Domain entity representing an individual monthly accounting period within a financial year.
 */
data class AccountingPeriod(
    val periodId: String,
    val companyId: String,
    val financialYearId: String,
    val name: String, // e.g. "April 2026", "Q1 FY26"
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: PeriodStatus = PeriodStatus.OPEN,
    val lockedAt: Long? = null,
    val lockedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(periodId.isNotBlank()) { "Period ID must not be blank" }
        require(companyId.isNotBlank()) { "Company ID must not be blank" }
        require(financialYearId.isNotBlank()) { "Financial Year ID must not be blank" }
        require(!startDate.isAfter(endDate)) { "Period startDate ($startDate) must not be after endDate ($endDate)" }
    }

    val isOpen: Boolean get() = status == PeriodStatus.OPEN
    val isLocked: Boolean get() = status != PeriodStatus.OPEN

    fun contains(date: LocalDate): Boolean {
        return !date.isBefore(startDate) && !date.isAfter(endDate)
    }

    fun validateOperationPermitted() {
        if (isLocked) {
            throw IllegalStateException("Accounting period '$name' is locked ($status). Operations cannot be performed.")
        }
    }
}

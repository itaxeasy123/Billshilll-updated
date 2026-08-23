package com.example.accounting.core.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Standardized Date Range object for Financial Years, Accounting Quarters, and Reporting intervals.
 */
data class DateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val label: String = ""
) {
    init {
        require(!endDate.isBefore(startDate)) { "End date $endDate cannot be before start date $startDate" }
    }

    fun contains(date: LocalDate): Boolean {
        return !date.isBefore(startDate) && !date.isAfter(endDate)
    }

    fun format(formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")): String {
        return "${startDate.format(formatter)} to ${endDate.format(formatter)}"
    }

    companion object {
        fun indianFinancialYear(startYear: Int): DateRange {
            val start = LocalDate.of(startYear, 4, 1)
            val end = LocalDate.of(startYear + 1, 3, 31)
            val fyCode = "FY ${startYear}-${(startYear + 1) % 100}"
            return DateRange(start, end, fyCode)
        }

        fun currentMonth(): DateRange {
            val now = LocalDate.now()
            val start = now.withDayOfMonth(1)
            val end = now.withDayOfMonth(now.lengthOfMonth())
            return DateRange(start, end, "${now.month.name} ${now.year}")
        }
    }
}

package com.example.accounting.application.reports

import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.party.PartyRole
import com.example.accounting.domain.reports.BalanceSheetReport
import com.example.accounting.domain.reports.CashFlowReport
import com.example.accounting.domain.reports.DayBookReport
import com.example.accounting.domain.reports.GSTSummaryReport
import com.example.accounting.domain.reports.OutstandingReport
import com.example.accounting.domain.reports.ProfitAndLossReport
import com.example.accounting.domain.reports.RatioAnalysisReport
import com.example.accounting.domain.reports.TrialBalanceReport
import java.time.LocalDate

/**
 * Application-service facade unifying every financial report behind one entry point (Phase 7J-B) -
 * every method is a direct, pure delegation to the existing, unmodified `AccountingRepository.generate*`
 * function (or, for [ratioAnalysis], [RatioAnalysisEngine.compute] via the repository's own
 * `generateRatioAnalysis` wrapper) - this class never recalculates a balance, tax figure, or ratio
 * itself, matching the "reports consume engine output, never recalculate" rule already structurally
 * enforced since Phase 7C. Every method takes an explicit `companyId`/`financialYearId` where the
 * underlying report is FY-scoped - never an implicit current company/FY.
 */
class ReportManagementService(private val repository: AccountingRepository) {

    suspend fun trialBalance(
        companyId: String,
        financialYearId: String,
        dateRange: ClosedRange<LocalDate>? = null,
        includeZeroBalance: Boolean = true
    ): TrialBalanceReport =
        repository.generateTrialBalance(companyId, financialYearId, dateRange, includeZeroBalance)

    suspend fun profitAndLoss(
        companyId: String,
        financialYearId: String,
        dateRange: ClosedRange<LocalDate>? = null
    ): ProfitAndLossReport =
        repository.generateProfitAndLoss(companyId, financialYearId, dateRange)

    suspend fun balanceSheet(
        companyId: String,
        financialYearId: String,
        dateRange: ClosedRange<LocalDate>? = null
    ): BalanceSheetReport =
        repository.generateBalanceSheet(companyId, financialYearId, dateRange)

    suspend fun gstSummary(companyId: String, financialYearId: String): GSTSummaryReport =
        repository.generateGSTSummary(companyId, financialYearId)

    suspend fun dayBook(companyId: String, dateRange: ClosedRange<LocalDate>): DayBookReport =
        repository.generateDayBook(companyId, dateRange)

    suspend fun outstanding(companyId: String, role: PartyRole? = null, today: LocalDate = LocalDate.now()): OutstandingReport =
        repository.generateOutstandingReport(companyId, role, today)

    suspend fun receivables(companyId: String, today: LocalDate = LocalDate.now()): OutstandingReport =
        repository.generateReceivablesReport(companyId, today)

    suspend fun payables(companyId: String, today: LocalDate = LocalDate.now()): OutstandingReport =
        repository.generatePayablesReport(companyId, today)

    suspend fun cashFlow(companyId: String, financialYearId: String, dateRange: ClosedRange<LocalDate>): CashFlowReport =
        repository.generateCashFlow(companyId, financialYearId, dateRange)

    suspend fun ratioAnalysis(
        companyId: String,
        financialYearId: String,
        dateRange: ClosedRange<LocalDate>? = null
    ): RatioAnalysisReport =
        repository.generateRatioAnalysis(companyId, financialYearId, dateRange)
}

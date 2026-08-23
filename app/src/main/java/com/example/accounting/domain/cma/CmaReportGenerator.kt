package com.example.accounting.domain.cma

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.Money
import com.example.accounting.domain.reports.BalanceSheetReport
import com.example.accounting.domain.reports.ProfitAndLossReport
import com.example.accounting.domain.reports.TrialBalanceReport
import com.example.accounting.domain.rendering.BusinessProfile

/**
 * A CMA (Credit Monitoring Arrangement) statement - the standard Indian bank-loan financial
 * summary format lenders require. Deliberately a **minimal, illustrative subset** of the full
 * multi-year CMA format (which also covers projected/estimated figures across several years) -
 * this phase only establishes the contract shape, not the complete statement.
 */
data class CmaReport(
    val companyId: String,
    val financialYearId: String,
    val currentAssets: Money,
    val currentLiabilities: Money,
    val netWorth: Money,
    val totalDebt: Money,
    val netSales: Money,
    val netProfit: Money,
    val currentRatio: Double,
    val debtEquityRatio: Double
)

/**
 * Contract for producing a [CmaReport] (Phase 7I) - a pure Kotlin interface only, no
 * implementation. Unlike [com.example.accounting.domain.ocr.OcrIngestionAdapter]/
 * [com.example.accounting.domain.reconciliation.ReconciliationAdapter] this isn't an external-
 * provider boundary at all - a CMA statement is entirely derived from the company's own books.
 * It is instead a sibling of Phase 7C's Report Architecture, and follows that phase's exact rule:
 * **reports consume engine output, they never recalculate**. [generate] takes the already-computed
 * [TrialBalanceReport]/[ProfitAndLossReport]/[BalanceSheetReport] as input - by construction, an
 * implementation of this interface has no way to derive a figure independently; it can only
 * re-shape numbers those three existing, frozen report functions already produced. No
 * `AccountingDao`/`AccountingRepository` reference belongs on this interface - a future
 * implementation would sit in `AccountingRepository` alongside `generateRatioAnalysis`, calling
 * this interface with report objects it already has, not the other way around.
 */
interface CmaReportGenerator {
    suspend fun generate(
        requestingCompany: BusinessProfile,
        trialBalance: TrialBalanceReport,
        profitAndLoss: ProfitAndLossReport,
        balanceSheet: BalanceSheetReport
    ): AccountingResult<CmaReport>
}

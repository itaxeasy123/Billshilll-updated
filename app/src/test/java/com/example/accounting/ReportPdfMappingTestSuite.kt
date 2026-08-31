package com.example.accounting

import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.domain.accounting.PrimaryGroup
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.invoice.InvoiceStatus
import com.example.accounting.domain.invoice.InvoiceType
import com.example.accounting.domain.reports.AgingBucket
import com.example.accounting.domain.reports.BalanceSheetReport
import com.example.accounting.domain.reports.DayBookEntryStatus
import com.example.accounting.domain.reports.DayBookReport
import com.example.accounting.domain.reports.DayBookRow
import com.example.accounting.domain.reports.ProfitAndLossReport
import com.example.accounting.domain.reports.TrialBalanceReport
import com.example.accounting.domain.reports.TrialBalanceRow
import com.example.accounting.domain.reports.toPdfData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Report Export Completion pass - [com.example.accounting.domain.reports.toPdfData] is pure
 * display formatting over an already-generated report model, never a second accounting
 * calculation. These tests construct report models directly (no DB/posting needed - the mapping
 * itself has no dependency on how the report was generated) and assert the PDF table data is
 * reachable and faithful to the source model's own totals.
 *
 * `TabularPdfRenderer.render()` itself (the actual `android.graphics.pdf.PdfDocument` call) is not
 * exercised here - same documented environment limitation as `PdfDocumentRenderer`
 * (`docs/44_PDF_PRINT_SHARE.md`): `android.graphics.pdf.*` has no real behavior under a plain JVM
 * unit test in this project's broken Robolectric setup.
 */
class ReportPdfMappingTestSuite {

    @Test
    fun dayBook_toPdfData_isReachableAndUsesExistingReportData() {
        val report = DayBookReport(
            dateRangeLabel = "01-Apr-2026 to 30-Apr-2026",
            rows = listOf(
                DayBookRow("V1", "INV-0001", VoucherType.SALES, LocalDate.of(2026, 4, 5), "Acme Traders", "Being goods sold", Money.fromPaise(118_00L), DayBookEntryStatus.POSTED),
                DayBookRow("V2", "PB-0001", VoucherType.PURCHASE, LocalDate.of(2026, 4, 10), "Beta Supplies", "Being goods purchased", Money.fromPaise(59_00L), DayBookEntryStatus.POSTED)
            ),
            totalAmount = Money.fromPaise(177_00L)
        )

        val data = report.toPdfData()

        assertEquals("Day Book", data.title)
        assertEquals(report.dateRangeLabel, data.subtitle)
        assertEquals(2, data.rows.size)
        // Every row's amount must be the SAME formatted figure the source DayBookRow already
        // carries - never a second sum.
        assertEquals(report.rows[0].totalAmount.formatPlain(), data.rows[0][5])
        assertEquals(report.rows[1].totalAmount.formatPlain(), data.rows[1][5])
        assertTrue("Totals row must carry the report's own totalAmount, not a re-derived sum", data.totalsRow!!.contains(report.totalAmount.formatPlain()))
    }

    @Test
    fun dayBook_toPdfData_emptyReport_producesHeaderOnlyNoCrash() {
        val report = DayBookReport(dateRangeLabel = "01-Apr-2026 to 30-Apr-2026", rows = emptyList(), totalAmount = Money.ZERO)
        val data = report.toPdfData()
        assertTrue(data.rows.isEmpty())
        assertEquals(Money.ZERO.formatPlain(), data.totalsRow!![5])
    }

    @Test
    fun trialBalance_toPdfData_handlesLargeRowCount_matchingSourceRowCount() {
        val rows = (1..250).map {
            TrialBalanceRow(
                ledgerId = "LED_$it", ledgerName = "Ledger $it", groupId = "GRP", groupName = "Group",
                primaryGroup = PrimaryGroup.ASSETS,
                closingDebit = Money.fromPaise(1000L), closingCredit = Money.ZERO
            )
        }
        val report = TrialBalanceReport(
            companyName = "Test Co", financialYearCode = "2026-27", asOfDate = LocalDate.of(2026, 4, 30),
            rows = rows, totalOpeningDebit = Money.ZERO, totalOpeningCredit = Money.ZERO,
            totalTransactionDebit = Money.ZERO, totalTransactionCredit = Money.ZERO,
            totalClosingDebit = Money.fromPaise(250_000L), totalClosingCredit = Money.fromPaise(250_000L)
        )

        val data = report.toPdfData()

        // 250 rows is well past what fits on one A4 page at this renderer's fixed row height -
        // proves the mapping itself has no row-count ceiling; TabularPdfRenderer's own newPage()
        // logic (not exercised here, see class doc) is what actually paginates this.
        assertEquals(250, data.rows.size)
        assertEquals(report.totalClosingDebit.formatPlain(), data.totalsRow!![6])
        assertEquals(report.totalClosingCredit.formatPlain(), data.totalsRow!![7])
    }

    @Test
    fun trialBalance_toPdfData_longLedgerName_isPassedThroughUntruncated() {
        // Truncation for display is TabularPdfRenderer's job (column-width-aware, needs a real
        // Paint to measure text) - the mapping itself must never pre-truncate, or CSV/JSON built
        // from the same report model would lose data too.
        val longName = "A Very Long Supplier Name Private Limited Company (Regd. Office - Industrial Area)"
        val report = TrialBalanceReport(
            companyName = "Test Co", financialYearCode = "2026-27", asOfDate = LocalDate.of(2026, 4, 30),
            rows = listOf(TrialBalanceRow("LED_1", longName, "GRP", "Group", PrimaryGroup.ASSETS, closingDebit = Money.fromPaise(500L))),
            totalOpeningDebit = Money.ZERO, totalOpeningCredit = Money.ZERO,
            totalTransactionDebit = Money.ZERO, totalTransactionCredit = Money.ZERO,
            totalClosingDebit = Money.fromPaise(500L), totalClosingCredit = Money.ZERO
        )
        assertEquals(longName, report.toPdfData().rows[0][0])
    }

    @Test
    fun profitAndLoss_toPdfData_totalsRowMatchesReportsOwnNetProfit_neverRecalculated() {
        val report = ProfitAndLossReport(
            companyName = "Test Co", financialYearCode = "2026-27", dateRange = "FY 2026-27",
            salesRevenue = Money.fromPaise(10_000_00L), directIncomes = Money.ZERO,
            purchases = Money.fromPaise(4_000_00L), directExpenses = Money.fromPaise(500_00L),
            grossProfit = Money.fromPaise(5_500_00L), indirectIncomes = Money.ZERO,
            indirectExpenses = Money.fromPaise(1_000_00L), netProfit = Money.fromPaise(4_500_00L)
        )
        val data = report.toPdfData()
        assertEquals(listOf("Net Profit", report.netProfit.formatPlain()), data.totalsRow)
    }

    @Test
    fun balanceSheet_toPdfData_totalsRowMatchesReportsOwnTotals_neverRecalculated() {
        val report = BalanceSheetReport(
            companyName = "Test Co", financialYearCode = "2026-27", asOfDate = LocalDate.of(2026, 4, 30),
            capitalAccounts = Money.fromPaise(10_000_00L), netProfitForYear = Money.fromPaise(1_000_00L),
            loansLiabilities = Money.ZERO, currentLiabilities = Money.fromPaise(500_00L),
            dutiesAndTaxesLiability = Money.ZERO, totalLiabilities = Money.fromPaise(11_500_00L),
            fixedAssets = Money.fromPaise(8_000_00L), currentAssets = Money.fromPaise(3_500_00L),
            sundryDebtors = Money.ZERO, bankAccounts = Money.ZERO, cashInHand = Money.ZERO,
            totalAssets = Money.fromPaise(11_500_00L)
        )
        val data = report.toPdfData()
        assertEquals(report.totalLiabilities.formatPlain(), data.totalsRow!![1])
        assertEquals(report.totalAssets.formatPlain(), data.totalsRow!![3])
    }
}

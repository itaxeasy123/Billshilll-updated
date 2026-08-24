package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.reports.ReportManagementService
import com.example.accounting.core.database.VoucherPostingEngine
import com.example.accounting.data.local.entity.JournalItemEntity
import com.example.accounting.data.local.entity.VoucherEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.SyncState
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.core.common.DrCr
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 7J-B - [ReportManagementService], unifying every financial report behind one entry point.
 * Every method is a direct, pure delegation to the existing, unmodified `AccountingRepository.generate*`
 * function - these tests prove the facade returns the exact same report the repository itself would
 * produce (delegation-equality), never a second computation.
 */
class Phase7JBReportManagementTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID
    private val fyId = Phase7JBFixtures.FY_ID
    private val fyRange = LocalDate.of(2026, 4, 1)..LocalDate.of(2027, 3, 31)

    private fun freshDao() = Phase7JBAwareDao(FakeAccountingDao())

    @Test
    fun testTrialBalance_delegatesExactly_emptyChartOfAccounts() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)

        val direct = repository.generateTrialBalance(companyId, fyId)
        val viaFacade = service.trialBalance(companyId, fyId)
        assertEquals(direct, viaFacade)
        assertTrue(viaFacade.isBalanced)
    }

    @Test
    fun testProfitAndLoss_delegatesExactly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)

        assertEquals(repository.generateProfitAndLoss(companyId, fyId), service.profitAndLoss(companyId, fyId))
    }

    @Test
    fun testBalanceSheet_delegatesExactly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)

        assertEquals(repository.generateBalanceSheet(companyId, fyId), service.balanceSheet(companyId, fyId))
    }

    @Test
    fun testGstSummary_delegatesExactly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)

        assertEquals(repository.generateGSTSummary(companyId, fyId), service.gstSummary(companyId, fyId))
    }

    @Test
    fun testDayBook_delegatesExactly_withRealPostedVoucher() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertLedger(Phase7JBFixtures.ledgerEntity("LED_CASH", companyId, StandardSystemGroups.CASH_GROUP_ID))
        dao.insertLedger(Phase7JBFixtures.ledgerEntity("LED_SALES", companyId, StandardSystemGroups.SALES_GROUP_ID, openingType = DrCr.CREDIT))
        VoucherPostingEngine.post(
            dao,
            VoucherEntity(
                voucherId = "V1", companyId = companyId, financialYearId = fyId, voucherNumber = "V1",
                voucherType = VoucherType.SALES, date = "2026-05-10", referenceNumber = "", narration = "",
                totalAmountPaise = 1_000_00L, isPosted = true, isCancelled = false, syncState = SyncState.PENDING,
                createdAt = 0L, updatedAt = 0L, createdBy = "TESTER", partyGstin = "", isGstApplicable = false
            ),
            listOf(
                JournalItemEntity("V1-1", "V1", companyId, fyId, "LED_CASH", DrCr.DEBIT, 1_000_00L, "", 1),
                JournalItemEntity("V1-2", "V1", companyId, fyId, "LED_SALES", DrCr.CREDIT, 1_000_00L, "", 2)
            ),
            "IK_V1", "TESTER"
        )

        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)
        val range = LocalDate.of(2026, 5, 1)..LocalDate.of(2026, 5, 31)
        assertEquals(repository.generateDayBook(companyId, range), service.dayBook(companyId, range))
    }

    @Test
    fun testOutstandingReceivablesPayables_delegateExactly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)

        assertEquals(repository.generateOutstandingReport(companyId), service.outstanding(companyId))
        assertEquals(repository.generateReceivablesReport(companyId), service.receivables(companyId))
        assertEquals(repository.generatePayablesReport(companyId), service.payables(companyId))
    }

    @Test
    fun testCashFlow_delegatesExactly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)

        assertEquals(repository.generateCashFlow(companyId, fyId, fyRange), service.cashFlow(companyId, fyId, fyRange))
    }

    @Test
    fun testRatioAnalysis_delegatesExactly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)

        assertEquals(repository.generateRatioAnalysis(companyId, fyId), service.ratioAnalysis(companyId, fyId))
    }

    private fun service(repository: AccountingRepository) = ReportManagementService(repository)
}

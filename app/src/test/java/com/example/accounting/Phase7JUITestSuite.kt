package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.reports.ReportManagementService
import com.example.accounting.application.voucher.VoucherDraftStatus
import com.example.accounting.application.voucher.VoucherManagementServiceImpl
import com.example.accounting.data.local.entity.GstTransactionEntity
import com.example.accounting.data.local.entity.VoucherDraftEntity
import com.example.accounting.data.local.entity.VoucherDraftLineEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.company.AccountingMode
import com.example.accounting.domain.company.BusinessType
import com.example.accounting.domain.company.Company
import com.example.accounting.domain.taxation.gst.GstDirection
import com.example.accounting.domain.taxation.gst.SupplyType
import com.example.accounting.core.common.DrCr
import com.example.accounting.presentation.viewmodel.AccountingUiState
import com.example.accounting.presentation.viewmodel.isInventoryEnabled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7J UI - pure-JVM coverage for the two genuinely new pieces of logic this phase added (the
 * screens themselves are Compose UI and not exercised here, matching every prior UI-adjacent
 * phase's own documented "real visual verification is not achievable in this environment"
 * disclosure - `GreetingScreenshotTest.kt` is already among the pre-existing Robolectric
 * environment failures). Everything else this phase touches is a pure delegation, already proven
 * correct by the Phase 7J-B suites this reuses (`Phase7JBFixtures`, `Phase7JBAwareDao`).
 */
class Phase7JUITestSuite {

    private fun freshDao() = Phase7JBAwareDao(FakeAccountingDao())

    // ---- isInventoryEnabled: the single Account-Mode gating point every Items-related call site
    // reads (ChartOfAccountsScreen's Items tab, Sales/Purchase item picker, Home's "Add Item"). ----

    @Test
    fun isInventoryEnabled_true_whenAccountWithInventory() {
        val company = Company(
            companyId = "C1", name = "Trading Co", tradeName = "Trading Co", gstin = "", pan = "",
            stateCode = "27", address = "", email = "", phone = "", currency = "INR",
            accountingMode = AccountingMode.ACCOUNT_WITH_INVENTORY, businessType = BusinessType.TRADING
        )
        val state = AccountingUiState(currentCompany = company)
        assertTrue(isInventoryEnabled(state))
    }

    @Test
    fun isInventoryEnabled_false_whenAccountOnly() {
        val company = Company(
            companyId = "C2", name = "Service Co", tradeName = "Service Co", gstin = "", pan = "",
            stateCode = "27", address = "", email = "", phone = "", currency = "INR",
            accountingMode = AccountingMode.ACCOUNT_ONLY, businessType = BusinessType.SERVICE
        )
        val state = AccountingUiState(currentCompany = company)
        assertFalse(isInventoryEnabled(state))
    }

    @Test
    fun isInventoryEnabled_false_whenNoCurrentCompany() {
        assertFalse(isInventoryEnabled(AccountingUiState(currentCompany = null)))
    }

    // ---- VoucherManagementServiceImpl.listDrafts - the one new additive method this phase added,
    // needed by the Money tab's Pending Reviews queue and OCR's prefill flow. Must map entity ->
    // domain correctly and filter by status, never touch the `vouchers` table. ----

    @Test
    fun listDrafts_returnsOnlyMatchingStatus_mappedCorrectly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val companyId = Phase7JBFixtures.COMPANY_ID
        val fyId = Phase7JBFixtures.FY_ID

        dao.insertVoucherDraft(
            VoucherDraftEntity(
                draftId = "D1", companyId = companyId, financialYearId = fyId,
                voucherType = VoucherType.PAYMENT, date = "2026-06-01", referenceNumber = "REF1",
                narration = "Pending draft", status = VoucherDraftStatus.PENDING_REVIEW,
                postedVoucherId = null, createdAt = 1L, updatedAt = 1L
            )
        )
        dao.insertVoucherDraftLines(
            listOf(
                VoucherDraftLineEntity(draftLineId = "L1", draftId = "D1", ledgerId = "LED1", type = DrCr.DEBIT, amountPaise = 50000L, narration = "", lineOrder = 0),
                VoucherDraftLineEntity(draftLineId = "L2", draftId = "D1", ledgerId = "LED2", type = DrCr.CREDIT, amountPaise = 50000L, narration = "", lineOrder = 1)
            )
        )
        dao.insertVoucherDraft(
            VoucherDraftEntity(
                draftId = "D2", companyId = companyId, financialYearId = fyId,
                voucherType = VoucherType.RECEIPT, date = "2026-06-02", referenceNumber = "",
                narration = "Already posted", status = VoucherDraftStatus.POSTED,
                postedVoucherId = "VCH_X", createdAt = 1L, updatedAt = 1L
            )
        )

        val repository = AccountingRepository(dao, db = null)
        val service = VoucherManagementServiceImpl(dao, repository)

        val pending = service.listDrafts(companyId, VoucherDraftStatus.PENDING_REVIEW).first()
        assertEquals(1, pending.size)
        assertEquals("D1", pending.single().draftId)
        assertEquals(2, pending.single().lines.size)
        assertEquals(50000L, pending.single().lines.first { it.type == DrCr.DEBIT }.amountPaise)

        val posted = service.listDrafts(companyId, VoucherDraftStatus.POSTED).first()
        assertEquals(1, posted.size)
        assertEquals("VCH_X", posted.single().postedVoucherId)
    }

    // ---- ReportManagementService.hsnSacSummary - the one new additive report method, a pure
    // grouping of already-computed GstTransaction rows by HSN/SAC code. Never a new tax
    // calculation - proves aggregation correctness, not tax math. ----

    private class GstTransactionAwareDao(private val delegate: com.example.accounting.data.local.dao.AccountingDao) :
        com.example.accounting.data.local.dao.AccountingDao by delegate {
        private val transactions = mutableListOf<GstTransactionEntity>()
        override suspend fun getGstTransactionsForCompanyFY(companyId: String, fyId: String) =
            transactions.filter { it.companyId == companyId && it.financialYearId == fyId }
        override suspend fun insertGstTransactions(newTransactions: List<GstTransactionEntity>) { transactions += newTransactions }
    }

    private fun gstTxn(id: String, companyId: String, fyId: String, hsn: String, direction: GstDirection, taxablePaise: Long, cgstPaise: Long, sgstPaise: Long) =
        GstTransactionEntity(
            gstTransactionId = id, companyId = companyId, financialYearId = fyId, voucherId = "V_$id",
            voucherType = VoucherType.SALES, partyLedgerId = "PARTY", partyGstin = "27AAAAA0000A1Z5",
            placeOfSupply = "27", supplyType = SupplyType.INTRA_STATE, itemId = null, hsnSacCode = hsn,
            quantityRaw = null, taxableAmountPaise = taxablePaise, gstRatePercent = 18.0,
            cgstPaise = cgstPaise, sgstPaise = sgstPaise, igstPaise = 0L, cessPaise = 0L,
            direction = direction, lineOrder = 0, createdAt = 1L
        )

    @Test
    fun hsnSacSummary_groupsByCode_neverRecomputesTax() = runBlocking {
        val dao = GstTransactionAwareDao(freshDao())
        dao.seedCompanyAndFy()
        val companyId = Phase7JBFixtures.COMPANY_ID
        val fyId = Phase7JBFixtures.FY_ID

        dao.insertGstTransactions(
            listOf(
                gstTxn("T1", companyId, fyId, "8471", GstDirection.OUTPUT, 100000L, 9000L, 9000L),
                gstTxn("T2", companyId, fyId, "8471", GstDirection.OUTPUT, 50000L, 4500L, 4500L),
                gstTxn("T3", companyId, fyId, "9983", GstDirection.INPUT, 20000L, 1800L, 1800L)
            )
        )

        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)
        val summary = service.hsnSacSummary(companyId, fyId)

        assertEquals(2, summary.size)
        val row8471 = summary.first { it.hsnSacCode == "8471" }
        assertEquals(150000L, row8471.outwardTaxableAmount.paise)
        assertEquals(0L, row8471.inwardTaxableAmount.paise)
        assertEquals(2, row8471.transactionCount)
        assertEquals(13500L, row8471.totalCgst.paise) // 9000 + 4500 - grouped, not recomputed
        val row9983 = summary.first { it.hsnSacCode == "9983" }
        assertEquals(20000L, row9983.inwardTaxableAmount.paise)
        assertEquals(1, row9983.transactionCount)
    }

    @Test
    fun hsnSacSummary_empty_whenNoGstTransactions() = runBlocking {
        val dao = GstTransactionAwareDao(freshDao())
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = ReportManagementService(repository)
        assertTrue(service.hsnSacSummary(Phase7JBFixtures.COMPANY_ID, Phase7JBFixtures.FY_ID).isEmpty())
    }
}

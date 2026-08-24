package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.settlement.SettlementManagementService
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.core.database.VoucherPostingEngine
import com.example.accounting.data.local.entity.JournalItemEntity
import com.example.accounting.data.local.entity.VoucherEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.SyncState
import com.example.accounting.domain.accounting.VoucherType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7J-B - [SettlementManagementService] ("Receipt/Payment"/"Settlement/Outstanding" scope) -
 * a thin, pure-delegation facade over the existing, unmodified [AccountingRepository.allocateSettlement]/
 * [AccountingRepository.getOutstandingInvoices]. Both underlying functions only need [dao] (real
 * `vouchers`/`settlement_allocations` backing) - no real `AppDatabase` required, since
 * [VoucherPostingEngine.post] is itself Room-independent (used here only to seed a real posted
 * voucher, mirroring [Phase7FTestSuite]'s identical `postedVoucher` helper pattern).
 */
class Phase7JBSettlementManagementTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID
    private val fyId = Phase7JBFixtures.FY_ID

    private fun freshDao() = Phase7JBAwareDao(FakeAccountingDao())

    private suspend fun com.example.accounting.data.local.dao.AccountingDao.seedLedgersAndFy() {
        seedCompanyAndFy()
        insertLedger(Phase7JBFixtures.ledgerEntity("LED_DEBTOR", companyId, StandardSystemGroups.DEBTORS_GROUP_ID))
        insertLedger(Phase7JBFixtures.ledgerEntity("LED_SALES", companyId, StandardSystemGroups.SALES_GROUP_ID, openingType = DrCr.CREDIT))
        insertLedger(Phase7JBFixtures.ledgerEntity("LED_CASH", companyId, StandardSystemGroups.CASH_GROUP_ID))
    }

    private suspend fun com.example.accounting.data.local.dao.AccountingDao.postVoucher(
        id: String, type: VoucherType, debitLedgerId: String, creditLedgerId: String, amountPaise: Long
    ) {
        val entity = VoucherEntity(
            voucherId = id, companyId = companyId, financialYearId = fyId, voucherNumber = id, voucherType = type,
            date = "2026-06-01", referenceNumber = "", narration = "", totalAmountPaise = amountPaise,
            isPosted = true, isCancelled = false, syncState = SyncState.PENDING, createdAt = 0L, updatedAt = 0L,
            createdBy = "TESTER", partyGstin = "", isGstApplicable = false
        )
        val items = listOf(
            JournalItemEntity("$id-1", id, companyId, fyId, debitLedgerId, DrCr.DEBIT, amountPaise, "", 1),
            JournalItemEntity("$id-2", id, companyId, fyId, creditLedgerId, DrCr.CREDIT, amountPaise, "", 2)
        )
        VoucherPostingEngine.post(this, entity, items, "IK_$id", "TESTER")
    }

    @Test
    fun testAllocateSettlement_fullAllocation_succeeds() = runBlocking {
        val dao = freshDao()
        dao.seedLedgersAndFy()
        dao.postVoucher("INV_1", VoucherType.SALES, "LED_DEBTOR", "LED_SALES", 5_000_00L)
        dao.postVoucher("RCP_1", VoucherType.RECEIPT, "LED_CASH", "LED_DEBTOR", 5_000_00L)

        val repository = AccountingRepository(dao, db = null)
        val service = SettlementManagementService(repository)

        val result = service.allocateSettlement(
            companyId, fyId, "RCP_1",
            listOf("INV_1" to Money.fromPaise(5_000_00L)), Money.ZERO
        )
        assertTrue(result is AccountingResult.Success)

        val outstanding = service.getOutstandingInvoices(companyId, "LED_DEBTOR")
        assertTrue("Invoice must no longer be outstanding after full allocation", outstanding.none { it.voucherId == "INV_1" })
    }

    @Test
    fun testAllocateSettlement_overAllocation_rejected() = runBlocking {
        val dao = freshDao()
        dao.seedLedgersAndFy()
        dao.postVoucher("INV_2", VoucherType.SALES, "LED_DEBTOR", "LED_SALES", 1_000_00L)
        dao.postVoucher("RCP_2", VoucherType.RECEIPT, "LED_CASH", "LED_DEBTOR", 5_000_00L)

        val repository = AccountingRepository(dao, db = null)
        val service = SettlementManagementService(repository)

        val result = service.allocateSettlement(
            companyId, fyId, "RCP_2",
            listOf("INV_2" to Money.fromPaise(5_000_00L)), Money.ZERO
        )
        assertTrue("Allocating beyond an invoice's own outstanding must be rejected", result is AccountingResult.Failure)
    }

    @Test
    fun testGetOutstandingInvoices_unpaidInvoice_appearsWithFullAmount() = runBlocking {
        val dao = freshDao()
        dao.seedLedgersAndFy()
        dao.postVoucher("INV_3", VoucherType.SALES, "LED_DEBTOR", "LED_SALES", 2_500_00L)

        val repository = AccountingRepository(dao, db = null)
        val service = SettlementManagementService(repository)

        val outstanding = service.getOutstandingInvoices(companyId, "LED_DEBTOR")
        assertEquals(1, outstanding.size)
        assertEquals(2_500_00L, outstanding.first().outstandingAmount.paise)
    }
}

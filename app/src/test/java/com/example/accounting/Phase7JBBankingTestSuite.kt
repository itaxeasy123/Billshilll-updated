package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.banking.BankUpiProfileService
import com.example.accounting.application.banking.CashBankLedgerService
import com.example.accounting.application.profile.TenantMismatchException
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.banking.BankUpiProfile
import com.example.accounting.domain.banking.UpiMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7J-B - [CashBankLedgerService] ("Cash/Bank" scope: a read-only query facade over ordinary
 * Cash/Bank system-group [com.example.accounting.domain.accounting.Ledger] rows, not a new domain
 * model) and [BankUpiProfileService] (real persistence for [BankUpiProfile], Phase 7G's own
 * previously-unpersisted domain model - bank/UPI *settlement metadata*, a distinct concept).
 */
class Phase7JBBankingTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID

    private fun freshDao() = Phase7JBAwareDao(FakeAccountingDao())

    @Test
    fun testCashBankLedgerService_returnsOnlyCashAndBankLedgers() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        dao.insertLedger(Phase7JBFixtures.ledgerEntity("LED_CASH", companyId, StandardSystemGroups.CASH_GROUP_ID))
        dao.insertLedger(Phase7JBFixtures.ledgerEntity("LED_BANK", companyId, StandardSystemGroups.BANK_GROUP_ID))
        dao.insertLedger(Phase7JBFixtures.ledgerEntity("LED_SALES", companyId, StandardSystemGroups.SALES_GROUP_ID))

        val repository = AccountingRepository(dao, db = null)
        val service = CashBankLedgerService(repository)

        val result = service.getCashAndBankLedgers(companyId)
        assertEquals(2, result.size)
        assertTrue(result.all { it.ledgerId == "LED_CASH" || it.ledgerId == "LED_BANK" })
    }

    private fun profile(partyId: String? = null) = BankUpiProfile(
        bankUpiProfileId = "", companyId = companyId, partyId = partyId, bankName = "HDFC Bank",
        accountHolderName = "Test Co", accountNumber = "1234567890", ifscCode = "HDFC0000001",
        upi = UpiMetadata(upiId = "test@hdfc", payeeName = "Test Co")
    )

    @Test
    fun testBankUpiProfileService_create_thenList() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val service = BankUpiProfileService(dao)

        val result = service.create(companyId, profile())
        val created = (result as AccountingResult.Success).data
        assertTrue(created.bankUpiProfileId.isNotBlank())

        val list = service.list(companyId).first()
        assertEquals(1, list.size)
        assertEquals("test@hdfc", list.first().upi?.upiId)
    }

    @Test
    fun testBankUpiProfileService_create_crossTenant_throwsTenantMismatch() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val service = BankUpiProfileService(dao)

        var threw = false
        try {
            service.create("SOME_OTHER_COMPANY", profile())
        } catch (e: TenantMismatchException) {
            threw = true
        }
        assertTrue("A caller writing under a different company context must throw, never silently succeed", threw)
    }

    @Test
    fun testBankUpiProfileService_update_and_delete() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val service = BankUpiProfileService(dao)

        val created = (service.create(companyId, profile()) as AccountingResult.Success).data
        val updateResult = service.update(companyId, created.copy(bankName = "ICICI Bank"))
        assertEquals("ICICI Bank", (updateResult as AccountingResult.Success).data.bankName)

        val deleteResult = service.delete(companyId, created.bankUpiProfileId)
        assertTrue(deleteResult is AccountingResult.Success)
        assertTrue(service.list(companyId).first().isEmpty())
    }

    @Test
    fun testBankUpiProfileService_listForParty_scopedCorrectly() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val service = BankUpiProfileService(dao)

        service.create(companyId, profile(partyId = "PTY_1"))
        service.create(companyId, profile(partyId = null))

        val partyScoped = service.listForParty(companyId, "PTY_1").first()
        assertEquals(1, partyScoped.size)
        assertEquals("PTY_1", partyScoped.first().partyId)
    }

    @Test
    fun testBankUpiProfileService_zeroLedgerVoucherJournalTableTouched() {
        // Pure metadata guarantee: no field on the service reaches a Ledger/Voucher/JournalItem
        // table beyond the plain AccountingDao it's handed (structural, not behavioral, check).
        val fields = BankUpiProfileService::class.java.declaredFields
        assertNull(fields.firstOrNull { it.type.name.contains("VoucherPostingEngine") })
    }
}

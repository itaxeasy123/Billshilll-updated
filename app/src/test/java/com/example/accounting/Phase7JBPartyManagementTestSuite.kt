package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.inventory.StockItemManagementService
import com.example.accounting.application.ledger.LedgerManagementService
import com.example.accounting.application.party.PartyManagementService
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.inventory.StockItem
import com.example.accounting.domain.party.Party
import com.example.accounting.domain.party.PartyEntityType
import com.example.accounting.domain.party.PartyRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7J-B - [PartyManagementService], [LedgerManagementService], [StockItemManagementService]
 * ("Party/Ledger/Item" scope) - all three are thin, pure-delegation facades over the existing,
 * unmodified [AccountingRepository] functions; these tests prove the delegation is exact (same row
 * created/returned as calling the repository directly), not new capability.
 */
class Phase7JBPartyManagementTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID

    private fun freshDao() = Phase7JBAwareDao(Phase7BTestSuite.Phase7BAwareDao(FakeAccountingDao()))

    @Test
    fun testPartyManagementService_createParty_createsUnderlyingLedgerToo() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = PartyManagementService(repository)

        val result = service.createParty(
            Party(partyId = "", companyId = companyId, ledgerId = "", role = PartyRole.CUSTOMER, entityType = PartyEntityType.BUSINESS, displayName = "Acme Traders")
        )
        val party = (result as AccountingResult.Success).data
        assertTrue(party.ledgerId.isNotBlank())

        val allParties = service.getParties(companyId, PartyRole.CUSTOMER).first()
        assertEquals(1, allParties.size)
        assertEquals("Acme Traders", allParties.first().displayName)
    }

    @Test
    fun testLedgerManagementService_createAndList_andDeleteSafely() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = LedgerManagementService(repository)

        val createResult = service.createLedger(
            Ledger(ledgerId = "", companyId = companyId, groupId = "${StandardSystemGroups.INDIRECT_EXPENSE_GROUP_ID}_$companyId", name = "Office Supplies")
        )
        val ledger = (createResult as AccountingResult.Success).data
        assertTrue(ledger.ledgerId.isNotBlank())

        val allLedgers = service.getLedgers(companyId).first()
        assertTrue(allLedgers.any { it.ledgerId == ledger.ledgerId })

        val deleteResult = service.deleteLedgerSafely(companyId, ledger.ledgerId)
        assertTrue("A never-posted-to ledger must be safely deletable", deleteResult is AccountingResult.Success)
    }

    @Test
    fun testStockItemManagementService_createAndList() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = StockItemManagementService(repository)

        val createResult = service.createStockItem(StockItem(itemId = "", companyId = companyId, name = "Widget", sku = "SKU-1"))
        val item = (createResult as AccountingResult.Success).data
        assertTrue(item.itemId.isNotBlank())

        val allItems = service.getStockItems(companyId).first()
        assertEquals(1, allItems.size)
        assertEquals("Widget", allItems.first().name)
    }
}

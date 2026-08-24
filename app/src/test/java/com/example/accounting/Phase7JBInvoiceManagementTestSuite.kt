package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.invoice.InvoiceFilter
import com.example.accounting.application.invoice.InvoiceManagementServiceImpl
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.Money
import com.example.accounting.core.common.Quantity
import com.example.accounting.data.local.entity.PartyEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.invoice.Invoice
import com.example.accounting.domain.invoice.InvoiceLine
import com.example.accounting.domain.invoice.InvoiceStatus
import com.example.accounting.domain.invoice.InvoiceType
import com.example.accounting.domain.party.PartyEntityType
import com.example.accounting.domain.party.PartyRole
import com.example.accounting.domain.party.PaymentTermsType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 7J-B - [InvoiceManagementServiceImpl] (real implementation of the frozen 7J-A
 * [com.example.accounting.application.invoice.InvoiceManagementService] interface). Every method
 * is a direct delegation to the existing, unmodified [AccountingRepository] functions plus the one
 * new, narrow [AccountingRepository.updateDraftInvoice]/[AccountingRepository.getInvoicesForCompany]/
 * [AccountingRepository.getInvoiceLines] additions this phase made.
 *
 * Composes [Phase7BTestSuite.Phase7BAwareDao] (real Party/Invoice/TradeDocument backing, Phase 7B)
 * with [Phase7JBAwareDao] (real `account_groups`/`document_assets`/Phase 7J-B table backing) -
 * mirrors [Phase7FTestSuite]'s own precedent of composing a prior phase's public `AwareDao`.
 */
class Phase7JBInvoiceManagementTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID
    private val fyId = Phase7JBFixtures.FY_ID
    private val partyId = "PTY_TEST"

    private fun freshDao() = Phase7JBAwareDao(Phase7BTestSuite.Phase7BAwareDao(FakeAccountingDao()))

    private suspend fun com.example.accounting.data.local.dao.AccountingDao.seedPartyAndFy() {
        seedCompanyAndFy()
        insertParty(
            PartyEntity(
                partyId = partyId, companyId = companyId, ledgerId = "LED_CUSTOMER_1", role = PartyRole.CUSTOMER,
                entityType = PartyEntityType.BUSINESS, displayName = "Test Customer", contactName = "",
                creditLimitPaise = null, paymentTermsType = PaymentTermsType.NET_30, paymentTermsCustomDays = null,
                isActive = true, createdAt = 0L, updatedAt = 0L
            )
        )
    }

    private fun sampleLine() = InvoiceLine(
        lineId = "", itemId = "ITEM_1", itemName = "Widget", hsnSacCode = "1234",
        quantity = Quantity(10), rate = Money.fromPaise(100_00L), gstRatePercent = 18.0
    )

    private fun sampleInvoice() = Invoice(
        invoiceId = "", companyId = companyId, financialYearId = fyId, invoiceType = InvoiceType.SALES_INVOICE,
        partyId = partyId, date = LocalDate.of(2026, 6, 1)
    )

    @Test
    fun testCreateDraft_delegatesToRepository_zeroAccountingEffect() = runBlocking {
        val dao = freshDao()
        dao.seedPartyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = InvoiceManagementServiceImpl(repository)

        val result = service.createDraft(sampleInvoice(), listOf(sampleLine()))
        val invoice = (result as AccountingResult.Success).data
        assertTrue(invoice.invoiceId.isNotBlank())
        assertNull(invoice.voucherId)
        assertTrue(dao.getVouchersByCompany(companyId).first().isEmpty())
    }

    @Test
    fun testUpdateDraft_stillDraft_replacesLines() = runBlocking {
        val dao = freshDao()
        dao.seedPartyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = InvoiceManagementServiceImpl(repository)

        val created = (service.createDraft(sampleInvoice(), listOf(sampleLine())) as AccountingResult.Success).data
        val newLine = sampleLine().copy(itemId = "ITEM_2", itemName = "Gadget", quantity = Quantity(5))
        val updateResult = service.updateDraft(created.copy(narration = "Updated"), listOf(newLine))
        assertTrue(updateResult is AccountingResult.Success)

        val linesResult = repository.getInvoiceLines(companyId, created.invoiceId)
        val lines = (linesResult as AccountingResult.Success).data
        assertEquals(1, lines.size)
        assertEquals("ITEM_2", lines.first().itemId)
    }

    @Test
    fun testUpdateDraft_alreadyPosted_rejected() = runBlocking {
        val dao = freshDao()
        dao.seedPartyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = InvoiceManagementServiceImpl(repository)

        val created = (service.createDraft(sampleInvoice(), listOf(sampleLine())) as AccountingResult.Success).data
        // Simulate posting by directly linking a voucherId (postVoucher itself needs a real DB,
        // covered separately - this test only proves updateDraftInvoice's own immutability guard).
        dao.linkInvoiceToVoucher(companyId, created.invoiceId, "VCH_FAKE", System.currentTimeMillis())

        val updateResult = service.updateDraft(created, listOf(sampleLine()))
        assertTrue("A posted invoice must never be editable", updateResult is AccountingResult.Failure)
    }

    @Test
    fun testDuplicateInvoice_freshDraft_neverLinkedToSource() = runBlocking {
        val dao = freshDao()
        dao.seedPartyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = InvoiceManagementServiceImpl(repository)

        val source = (service.createDraft(sampleInvoice(), listOf(sampleLine())) as AccountingResult.Success).data
        val duplicateResult = service.duplicateInvoice(companyId, source.invoiceId)
        val duplicate = (duplicateResult as AccountingResult.Success).data

        assertTrue(duplicate.invoiceId != source.invoiceId)
        assertNull(duplicate.referenceInvoiceId)
        assertNull(duplicate.voucherId)

        val duplicateLines = (repository.getInvoiceLines(companyId, duplicate.invoiceId) as AccountingResult.Success).data
        assertEquals(1, duplicateLines.size)
        assertEquals("ITEM_1", duplicateLines.first().itemId)
    }

    @Test
    fun testCancelInvoice_stillDraft_deletesOutright() = runBlocking {
        val dao = freshDao()
        dao.seedPartyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = InvoiceManagementServiceImpl(repository)

        val created = (service.createDraft(sampleInvoice(), listOf(sampleLine())) as AccountingResult.Success).data
        val cancelResult = service.cancelInvoice(companyId, fyId, created.invoiceId)
        assertTrue(cancelResult is AccountingResult.Success)
        assertTrue(repository.getInvoicesForCompany(companyId).first().none { it.invoiceId == created.invoiceId })
    }

    @Test
    fun testSearch_byPartyIdAndQuery_filtersCorrectly() = runBlocking {
        val dao = freshDao()
        dao.seedPartyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = InvoiceManagementServiceImpl(repository)

        service.createDraft(sampleInvoice().copy(narration = "Alpha shipment"), listOf(sampleLine()))
        service.createDraft(sampleInvoice().copy(narration = "Beta shipment"), listOf(sampleLine()))

        val results = service.search(companyId, InvoiceFilter(query = "Alpha", partyId = partyId))
        val invoices = (results as AccountingResult.Success).data
        assertEquals(1, invoices.size)
        assertEquals("Alpha shipment", invoices.first().narration)
    }

    @Test
    fun testSearch_byStatus_derivesViaInvoiceStatusEngine_neverStored() = runBlocking {
        val dao = freshDao()
        dao.seedPartyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val service = InvoiceManagementServiceImpl(repository)

        service.createDraft(sampleInvoice(), listOf(sampleLine()))

        val draftResults = (service.search(companyId, InvoiceFilter(state = InvoiceStatus.DRAFT)) as AccountingResult.Success).data
        assertEquals(1, draftResults.size)

        val paidResults = (service.search(companyId, InvoiceFilter(state = InvoiceStatus.PAID)) as AccountingResult.Success).data
        assertTrue(paidResults.isEmpty())
    }
}

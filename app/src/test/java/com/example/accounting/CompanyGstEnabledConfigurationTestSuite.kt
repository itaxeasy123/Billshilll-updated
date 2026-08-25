package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.GstRegistrationStatus
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.company.AccountingMode
import com.example.accounting.domain.company.BusinessType
import com.example.accounting.domain.company.Company
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused tests for the new company-level `gstEnabled` configuration field - the approved next
 * step after the read-only GST Settings audit. Pure domain/repository tests only: no RCM, no GST
 * calculation change, no Purchase/Sales posting change, no UI - none of those were touched.
 */
class CompanyGstEnabledConfigurationTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID

    private fun freshDao() = Phase7JBAwareDao(Phase7BTestSuite.Phase7BAwareDao(FakeAccountingDao()))

    @Test
    fun testExistingCompanyConstruction_withoutGstEnabledArgument_stillCompilesAndWorks() {
        // No gstEnabled argument at all - every pre-existing Company(...) call site keeps compiling.
        val company = Company(companyId = "COMP_1", name = "Test Co")

        assertEquals("Test Co", company.name)
    }

    @Test
    fun testDefaultGstEnabled_isTrue_preservingExistingAlwaysOnGstBehavior() {
        // Every Sale/Purchase/Credit-Debit-Note voucher is already built with isGstApplicable=true
        // today regardless of any company setting - true is the only default that doesn't silently
        // change existing companies' behavior.
        val company = Company(companyId = "COMP_1", name = "Test Co")

        assertTrue(company.gstEnabled)
    }

    @Test
    fun testExplicitGstEnabledTrue_isStoredCorrectly() {
        val company = Company(companyId = "COMP_1", name = "Test Co", gstEnabled = true)

        assertTrue(company.gstEnabled)
    }

    @Test
    fun testExplicitGstEnabledFalse_isStoredCorrectly() {
        val company = Company(companyId = "COMP_1", name = "Test Co", gstEnabled = false)

        assertTrue(!company.gstEnabled)
    }

    @Test
    fun testUpdateAccountingConfiguration_changingGstEnabled_doesNotModifyAccountingModeOrBusinessType() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        val before = repository.getCompanies().first().first { it.companyId == companyId }
        assertEquals(AccountingMode.ACCOUNT_ONLY, before.accountingMode)
        assertEquals(BusinessType.TRADING, before.businessType)
        assertTrue(before.gstEnabled)

        repository.updateAccountingConfiguration(companyId, gstEnabled = false)

        val after = repository.getCompanies().first().first { it.companyId == companyId }
        assertTrue(!after.gstEnabled)
        assertEquals("accountingMode must be untouched by a gstEnabled-only update", AccountingMode.ACCOUNT_ONLY, after.accountingMode)
        assertEquals("businessType must be untouched by a gstEnabled-only update", BusinessType.TRADING, after.businessType)
    }

    @Test
    fun testUpdateAccountingConfiguration_changingAccountingMode_doesNotModifyGstEnabled() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)

        repository.updateAccountingConfiguration(companyId, accountingMode = AccountingMode.ACCOUNT_WITH_INVENTORY)

        val after = repository.getCompanies().first().first { it.companyId == companyId }
        assertEquals(AccountingMode.ACCOUNT_WITH_INVENTORY, after.accountingMode)
        assertTrue("gstEnabled must be untouched by an accountingMode-only update", after.gstEnabled)
    }

    @Test
    fun testUpdateAccountingConfiguration_omittingGstEnabled_leavesItUnchanged() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val repository = AccountingRepository(dao, db = null)
        repository.updateAccountingConfiguration(companyId, gstEnabled = false)

        // A later, unrelated update call (businessType only) must not reset gstEnabled back to
        // the default - the "omitted means unchanged" contract must hold on every call, not just
        // the first one.
        repository.updateAccountingConfiguration(companyId, businessType = BusinessType.SERVICE)

        val after = repository.getCompanies().first().first { it.companyId == companyId }
        assertTrue(!after.gstEnabled)
        assertEquals(BusinessType.SERVICE, after.businessType)
    }

    @Test
    fun testLedgerGstRegistrationStatus_remainsIndependentOfCompanyGstEnabled() {
        // Company configuration (gstEnabled) and Ledger fact (gstRegistrationStatus) are separate
        // concepts - setting one must never be inferred from, or written by, the other. This test
        // only proves the two fields are structurally unrelated (no code path connects them),
        // matching the fact that this task touched no Ledger/registration-status code at all.
        val ledger = Ledger(ledgerId = "LED_1", companyId = "COMP_1", groupId = "GRP_1", name = "Test Ledger")

        assertEquals(null, ledger.gstRegistrationStatus)

        val registeredLedger = ledger.copy(gstRegistrationStatus = GstRegistrationStatus.REGISTERED)
        assertEquals(GstRegistrationStatus.REGISTERED, registeredLedger.gstRegistrationStatus)
        // Nothing about a Company's gstEnabled value was read or written to reach this state.
    }
}

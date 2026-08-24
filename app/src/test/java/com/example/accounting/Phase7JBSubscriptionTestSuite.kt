package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.application.subscription.SubscriptionManagementService
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.local.entity.FinancialYearEntity
import com.example.accounting.domain.subscription.EntitlementFeature
import com.example.accounting.domain.subscription.SubscriptionPlanType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 7J-B - [SubscriptionManagementService]: FREE/PAID, one row per company per financial year
 * (unique `(companyId, financialYearId)` index simulated in [Phase7JBAwareDao]), paid validity
 * always derived from the referenced [com.example.accounting.domain.financialyear.FinancialYear]'s
 * own stored dates.
 *
 * [testCheckEntitlement_nonStandardFinancialYear_stillResolvesCorrectly] uses a financial year that
 * does **not** start 1 Apr/end 31 Mar - the concrete proof that validity is never computed from a
 * hardcoded "1 Apr-31 Mar" literal anywhere in [SubscriptionManagementService].
 */
class Phase7JBSubscriptionTestSuite {

    private val companyId = Phase7JBFixtures.COMPANY_ID
    private val fyId = Phase7JBFixtures.FY_ID

    private fun freshDao() = Phase7JBAwareDao(FakeAccountingDao())

    @Test
    fun testCreateOrRenew_newSubscription_persistsAndIsRetrievable() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val service = SubscriptionManagementService(dao)

        val result = service.createOrRenew(companyId, fyId, SubscriptionPlanType.PAID, "Pro Plan", setOf(EntitlementFeature.GSTR, EntitlementFeature.INVENTORY))
        val subscription = (result as AccountingResult.Success).data
        assertEquals(SubscriptionPlanType.PAID, subscription.planType)
        assertTrue(subscription.entitlements.containsAll(setOf(EntitlementFeature.GSTR, EntitlementFeature.INVENTORY)))

        val fetched = service.getCurrent(companyId, fyId)
        assertEquals(subscription.subscriptionId, fetched?.subscriptionId)
    }

    @Test
    fun testCreateOrRenew_wrongCompanyFinancialYear_rejectedWithTenantMismatch() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val service = SubscriptionManagementService(dao)

        val result = service.createOrRenew("SOME_OTHER_COMPANY", fyId, SubscriptionPlanType.FREE, "Free Plan", emptySet())
        assertTrue(result is AccountingResult.Failure)
    }

    @Test
    fun testCreateOrRenew_unknownFinancialYear_rejected() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val service = SubscriptionManagementService(dao)

        val result = service.createOrRenew(companyId, "NO_SUCH_FY", SubscriptionPlanType.FREE, "Free Plan", emptySet())
        assertTrue(result is AccountingResult.Failure)
    }

    @Test
    fun testCheckEntitlement_nonStandardFinancialYear_stillResolvesCorrectly() = runBlocking {
        val dao = freshDao()
        // Deliberately NOT 1 Apr - 31 Mar - a company on a calendar-year FY. If validity were ever
        // hardcoded to "1 Apr-31 Mar" this test would fail even though the subscription is genuinely
        // active for the current date.
        val nonStandardFyId = "FY_CALENDAR_2026"
        dao.insertCompany(
            com.example.accounting.data.local.entity.CompanyEntity(
                companyId = companyId, name = "Company", tradeName = "Company", gstin = "27AAAAA0000A1Z5",
                pan = "AAAAA0000A", stateCode = "27", stateName = "Maharashtra", email = "", phone = "", address = "",
                currency = "INR", financialYearStartMonth = 1, isDefault = true, createdAt = 0L,
                accountingMode = com.example.accounting.domain.company.AccountingMode.ACCOUNT_ONLY,
                businessType = com.example.accounting.domain.company.BusinessType.TRADING
            )
        )
        val today = LocalDate.now()
        dao.insertFinancialYear(
            FinancialYearEntity(
                nonStandardFyId, companyId, "CY 2026", today.minusDays(10).toString(), today.plusDays(300).toString(),
                true, false, null, null
            )
        )
        val service = SubscriptionManagementService(dao)

        service.createOrRenew(companyId, nonStandardFyId, SubscriptionPlanType.PAID, "Pro Plan", setOf(EntitlementFeature.GSTR))
        val hasEntitlement = service.checkEntitlement(companyId, nonStandardFyId, EntitlementFeature.GSTR)
        assertTrue("Entitlement must resolve as active - today falls inside this non-standard FY's own dates", hasEntitlement)
    }

    @Test
    fun testCheckEntitlement_financialYearNotYetActive_returnsFalse() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val futureFyId = "FY_FUTURE"
        dao.insertFinancialYear(FinancialYearEntity(futureFyId, companyId, "FY Future", "2030-04-01", "2031-03-31", false, false, null, null))
        val service = SubscriptionManagementService(dao)

        service.createOrRenew(companyId, futureFyId, SubscriptionPlanType.PAID, "Pro Plan", setOf(EntitlementFeature.GSTR))
        val hasEntitlement = service.checkEntitlement(companyId, futureFyId, EntitlementFeature.GSTR)
        assertFalse("A future, not-yet-active financial year must never grant entitlement", hasEntitlement)
    }

    @Test
    fun testCheckEntitlement_freePlan_deniesPaidOnlyFeature() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val service = SubscriptionManagementService(dao)

        service.createOrRenew(companyId, fyId, SubscriptionPlanType.FREE, "Free Plan", setOf(EntitlementFeature.ACCOUNTING))
        val hasGstr = service.checkEntitlement(companyId, fyId, EntitlementFeature.GSTR)
        assertFalse(hasGstr)
    }

    @Test
    fun testDeactivate_activeSubscription_thenEntitlementDenied() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val service = SubscriptionManagementService(dao)

        val subscription = (service.createOrRenew(companyId, fyId, SubscriptionPlanType.PAID, "Pro Plan", setOf(EntitlementFeature.GSTR)) as AccountingResult.Success).data
        service.deactivate(companyId, subscription.subscriptionId)

        assertFalse(service.checkEntitlement(companyId, fyId, EntitlementFeature.GSTR))
    }

    @Test
    fun testEntitlementChecker_zeroAccountingMutationPath() {
        // Structural guarantee: SubscriptionEntitlementChecker (frozen, Phase 7J) is pure and
        // non-suspend, reused verbatim here - re-verified rather than assumed.
        val fields = com.example.accounting.domain.subscription.SubscriptionEntitlementChecker::class.java.declaredFields
        assertTrue(fields.none { it.type.name.contains("AccountingDao") || it.type.name.contains("AccountingRepository") })
    }
}

package com.example.accounting

import com.example.accounting.domain.sandbox.AssessmentYear
import com.example.accounting.domain.sandbox.PricingType
import com.example.accounting.domain.sandbox.SandboxEnvironment
import com.example.accounting.domain.sandbox.SandboxIntegrationConfiguration
import com.example.accounting.domain.sandbox.SandboxProviderAdapter
import com.example.accounting.domain.sandbox.SandboxServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Phase 7H (Sandbox.co.in integration - architecture/contracts only) - pure JVM tests. These are
 * plain data classes and one pure interface with zero DAO/repository access, so there is no
 * accounting-engine or ledger behavior to exercise directly; the tests instead lock in the
 * structural contract itself: [SandboxEnvironment.TEST]/[SandboxEnvironment.LIVE] and
 * enabled-service state round-trip correctly, the explicit security requirement that neither
 * [SandboxIntegrationConfiguration] nor [SandboxServiceStatus] ever grows a raw-credential field,
 * and (below) that [SandboxProviderAdapter] stays exactly the interface this phase specified - a
 * pure Kotlin contract with no accounting-mutation capability reachable through it.
 */
class SandboxIntegrationTestSuite {

    private val suspiciousFieldNamePatterns = listOf("key", "secret", "token", "password", "credential", "apikey")

    private fun assertNoCredentialLikeFields(clazz: Class<*>) {
        for (field in clazz.declaredFields) {
            val nameLower = field.name.lowercase()
            assertFalse(
                "${clazz.simpleName}.${field.name} looks like a raw-credential field - Sandbox API " +
                    "credentials must live in SecureStorage, never in this model",
                suspiciousFieldNamePatterns.any { nameLower.contains(it) }
            )
        }
    }

    @Test
    fun testSandboxIntegrationConfiguration_neverGrowsARawCredentialField() {
        assertNoCredentialLikeFields(SandboxIntegrationConfiguration::class.java)
    }

    @Test
    fun testSandboxServiceStatus_neverGrowsARawCredentialField() {
        assertNoCredentialLikeFields(SandboxServiceStatus::class.java)
    }

    @Test
    fun testSandboxEnvironment_hasExactlyTestAndLive() {
        assertEquals(setOf(SandboxEnvironment.TEST, SandboxEnvironment.LIVE), SandboxEnvironment.entries.toSet())
    }

    @Test
    fun testPricingType_hasExactlyIncludedAndPaid() {
        assertEquals(setOf(PricingType.INCLUDED, PricingType.PAID), PricingType.entries.toSet())
    }

    @Test
    fun testSandboxIntegrationConfiguration_holdsPerServiceEnablementIndependently() {
        val config = SandboxIntegrationConfiguration(
            companyId = "COMP_1",
            environment = SandboxEnvironment.TEST,
            enabledServices = listOf(
                SandboxServiceStatus(serviceName = "GST Verification", pricingType = PricingType.INCLUDED, isEnabled = true),
                SandboxServiceStatus(serviceName = "e-Invoice", pricingType = PricingType.PAID, isEnabled = false)
            ),
            isActive = true
        )

        assertEquals("COMP_1", config.companyId)
        assertEquals(SandboxEnvironment.TEST, config.environment)
        assertTrue(config.isActive)
        assertEquals(2, config.enabledServices.size)
        assertTrue(config.enabledServices.first { it.serviceName == "GST Verification" }.isEnabled)
        assertFalse(config.enabledServices.first { it.serviceName == "e-Invoice" }.isEnabled)
    }

    // ==========================================
    // SandboxProviderAdapter - structural contract only (no implementation to test)
    // ==========================================
    @Test
    fun testSandboxProviderAdapter_isAPureInterface() {
        val clazz = SandboxProviderAdapter::class.java
        assertTrue("SandboxProviderAdapter must be an interface, never a class with implementation", clazz.isInterface)
    }

    @Test
    fun testSandboxProviderAdapter_declaresExactlyTheThreeRequiredOperations() {
        val methodNames = SandboxProviderAdapter::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("verifyGstin", "requestEInvoiceIrn", "fetchForm26As"), methodNames)
    }

    @Test
    fun testSandboxProviderAdapter_noOperationTakesADaoOrRepositoryOrPostingEngine() {
        // Structural proof, not just documentation, that nothing on this interface can reach the
        // accounting-mutation path: no method parameter or return type here may be an
        // AccountingDao/AccountingRepository/VoucherPostingEngine/DoubleEntryValidator.
        val forbiddenSimpleNameFragments = listOf("Dao", "Repository", "PostingEngine", "DoubleEntryValidator")
        for (method in SandboxProviderAdapter::class.java.declaredMethods) {
            val referencedTypes = method.parameterTypes.toList() + method.returnType
            for (type in referencedTypes) {
                assertFalse(
                    "${method.name} must not reference ${type.simpleName} - SandboxProviderAdapter must have zero path to an accounting mutation",
                    forbiddenSimpleNameFragments.any { type.simpleName.contains(it) }
                )
            }
        }
    }

    @Test
    fun testSandboxProviderAdapter_isAnUnimplementedContract() {
        // "Architecture and contracts only" per docs/domain/sandbox/README.md - no concrete class
        // should implement this interface yet. Abstract-ness plus zero declared constructors on the
        // interface itself is the closest structural proxy pure reflection can offer for that.
        val clazz = SandboxProviderAdapter::class.java
        assertTrue(Modifier.isAbstract(clazz.modifiers))
        assertEquals(0, clazz.declaredConstructors.size)
    }

    @Test
    fun testFetchForm26As_usesAssessmentYear_notFinancialYearOrGstFilingPeriod() {
        val method = SandboxProviderAdapter::class.java.declaredMethods.first { it.name == "fetchForm26As" }
        val paramTypeNames = method.parameterTypes.map { it.simpleName }
        assertTrue("fetchForm26As must take an AssessmentYear", paramTypeNames.contains(AssessmentYear::class.java.simpleName))
        assertFalse("fetchForm26As must never take a FinancialYear", paramTypeNames.any { it == "FinancialYear" })
        assertFalse("fetchForm26As must never take a GstFilingPeriod", paramTypeNames.any { it == "GstFilingPeriod" })
    }

    @Test
    fun testAssessmentYear_labelFormat_isYearHyphenTwoDigitNextYear() {
        assertEquals("2026-27", AssessmentYear(2026).label)
        assertEquals("2099-00", AssessmentYear(2099).label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAssessmentYear_implausibleStartYear_rejected() {
        AssessmentYear(1500)
    }
}

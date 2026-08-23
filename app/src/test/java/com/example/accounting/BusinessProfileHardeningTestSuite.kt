package com.example.accounting

import com.example.accounting.application.profile.ProfileApplicationService
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.entity.AccountingPeriodEntity
import com.example.accounting.data.local.entity.BusinessProfileEntity
import com.example.accounting.data.local.entity.CompanyEntity
import com.example.accounting.data.local.entity.FinancialYearEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.company.AccountingMode
import com.example.accounting.domain.company.BusinessType
import com.example.accounting.domain.financialyear.PeriodStatus
import com.example.accounting.domain.rendering.BusinessProfile
import com.example.accounting.domain.rendering.CapitalAccountNaming
import com.example.accounting.domain.rendering.ConstitutionType
import com.example.accounting.domain.rendering.Gstin
import com.example.accounting.domain.rendering.Pan
import com.example.accounting.domain.rendering.Tan
import com.example.accounting.domain.rendering.Udyam
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests the Business Profile hardening: [ConstitutionType]/`tan`/`udyam` on [BusinessProfile],
 * the [Pan]/[Gstin]/[Tan]/[Udyam] structured identifier value types, and the
 * [CapitalAccountNaming] rule.
 */
class BusinessProfileHardeningTestSuite {

    private class ProfileAwareDao(delegate: AccountingDao) : AccountingDao by delegate {
        private val businessProfiles = mutableMapOf<String, BusinessProfileEntity>()
        override suspend fun getBusinessProfile(companyId: String) = businessProfiles[companyId]
        override suspend fun insertBusinessProfile(profile: BusinessProfileEntity) { businessProfiles[profile.companyId] = profile }
        override suspend fun updateBusinessProfile(
            companyId: String, businessProfileId: String, businessName: String, legalName: String,
            constitutionType: ConstitutionType, address: String,
            phone: String, email: String, website: String, gstin: String, pan: String, tan: String, udyam: String, logoAssetId: String?,
            bankName: String, bankAccountNumber: String, bankIfsc: String, bankBranch: String, upiId: String,
            qrCodeAssetId: String?, signatureAssetId: String?, termsAndConditions: String, updatedAt: Long
        ) {
            val existing = businessProfiles[companyId] ?: return
            if (existing.businessProfileId != businessProfileId) return
            businessProfiles[companyId] = existing.copy(
                businessName = businessName, legalName = legalName, constitutionType = constitutionType, address = address,
                phone = phone, email = email, website = website, gstin = gstin, pan = pan, tan = tan, udyam = udyam,
                logoAssetId = logoAssetId, bankName = bankName, bankAccountNumber = bankAccountNumber, bankIfsc = bankIfsc,
                bankBranch = bankBranch, upiId = upiId, qrCodeAssetId = qrCodeAssetId, signatureAssetId = signatureAssetId,
                termsAndConditions = termsAndConditions, updatedAt = updatedAt
            )
        }
    }

    private val companyId = "COMP_HARDEN"
    private val fyId = "FY_HARDEN_2026_27"

    private fun freshDao() = ProfileAwareDao(FakeAccountingDao())

    private suspend fun AccountingDao.seed() {
        insertCompany(CompanyEntity(
            companyId = companyId, name = "Test Co", tradeName = "Test Co", gstin = "27AAAAA0000A1Z5",
            pan = "AAAAA0000A", stateCode = "27", stateName = "Maharashtra", email = "", phone = "", address = "",
            currency = "INR", financialYearStartMonth = 4, isDefault = true, createdAt = 0L,
            accountingMode = AccountingMode.ACCOUNT_ONLY, businessType = BusinessType.TRADING
        ))
        insertFinancialYear(FinancialYearEntity(fyId, companyId, "FY 2026-27", "2026-04-01", "2027-03-31", true, false, null, null))
        insertPeriods(listOf(AccountingPeriodEntity("PER_$companyId", companyId, fyId, "Full Year", "2026-04-01", "2027-03-31", PeriodStatus.OPEN, null, null)))
    }

    // ==========================================
    // CapitalAccountNaming
    // ==========================================
    @Test
    fun testResolveCapitalAccountName_proprietorship_usesExactPersonNameFormat() {
        assertEquals("Capital - Ramesh Kumar", CapitalAccountNaming.resolveCapitalAccountName(ConstitutionType.PROPRIETORSHIP, "Ramesh Kumar"))
    }

    @Test
    fun testResolveCapitalAccountName_proprietorship_blankNameThrows() {
        try {
            CapitalAccountNaming.resolveCapitalAccountName(ConstitutionType.PROPRIETORSHIP, null)
            fail("Expected IllegalArgumentException for a proprietorship with no person name")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            CapitalAccountNaming.resolveCapitalAccountName(ConstitutionType.PROPRIETORSHIP, "   ")
            fail("Expected IllegalArgumentException for a blank person name")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun testResolveCapitalAccountName_privateLimited_isShareCapitalAccount() {
        assertEquals("Share Capital Account", CapitalAccountNaming.resolveCapitalAccountName(ConstitutionType.PRIVATE_LIMITED))
        assertEquals("Share Capital Account", CapitalAccountNaming.resolveCapitalAccountName(ConstitutionType.PUBLIC_LIMITED))
    }

    @Test
    fun testResolveCapitalAccountName_nonCorporateNonProprietorship_isGenericCapitalAccount_neverShareCapital() {
        for (constitution in listOf(ConstitutionType.PARTNERSHIP, ConstitutionType.LLP, ConstitutionType.HUF, ConstitutionType.TRUST, ConstitutionType.SOCIETY, ConstitutionType.OTHER)) {
            val name = CapitalAccountNaming.resolveCapitalAccountName(constitution)
            assertEquals("Capital Account", name)
            assertFalse("$constitution must never produce 'Share Capital Account'", name.contains("Share"))
        }
    }

    @Test
    fun testRequiresShareCapital_onlyTrueForCorporateConstitutions() {
        assertTrue(CapitalAccountNaming.requiresShareCapital(ConstitutionType.PRIVATE_LIMITED))
        assertTrue(CapitalAccountNaming.requiresShareCapital(ConstitutionType.PUBLIC_LIMITED))
        assertFalse(CapitalAccountNaming.requiresShareCapital(ConstitutionType.PROPRIETORSHIP))
        assertFalse(CapitalAccountNaming.requiresShareCapital(ConstitutionType.PARTNERSHIP))
        assertFalse(CapitalAccountNaming.requiresShareCapital(ConstitutionType.LLP))
    }

    // ==========================================
    // Structured identifiers - Pan/Gstin/Tan/Udyam
    // ==========================================
    @Test
    fun testPan_validAndInvalidFormats() {
        assertEquals("ABCDE1234F", Pan.from("ABCDE1234F")?.value)
        assertEquals("ABCDE1234F", Pan.from("abcde1234f")?.value) // case-insensitive input
        assertNull(Pan.from("INVALID"))
        assertNull(Pan.from(""))
        assertTrue(Pan.isValid("ABCDE1234F"))
        assertFalse(Pan.isValid("12345ABCDE"))
    }

    @Test
    fun testGstin_validFormatExposesStateCodeAndEmbeddedPan() {
        val gstin = Gstin.from("27AAAAA0000A1Z5")
        assertEquals("27AAAAA0000A1Z5", gstin?.value)
        assertEquals("27", gstin?.stateCode)
        assertEquals("AAAAA0000A", gstin?.embeddedPan)
        assertNull(Gstin.from("NOT-A-GSTIN"))
    }

    @Test
    fun testTan_validAndInvalidFormats() {
        assertEquals("ABCD12345E", Tan.from("ABCD12345E")?.value)
        assertNull(Tan.from("TOO-SHORT"))
    }

    @Test
    fun testUdyam_validAndInvalidFormats_andIsOptional() {
        assertEquals("UDYAM-MH-01-0001234", Udyam.from("UDYAM-MH-01-0001234")?.value)
        assertNull(Udyam.from(""))
        assertNull(Udyam.from("NOT-A-UDYAM-NUMBER"))
    }

    // ==========================================
    // BusinessProfile round-trip through ProfileApplicationService
    // ==========================================
    @Test
    fun testBusinessProfile_constitutionTypeTanUdyam_persistThroughUpsertAndGet() = runBlocking {
        val dao = freshDao()
        dao.seed()
        val service = ProfileApplicationService(AccountingRepository(dao))

        val result = service.upsertBusinessProfile(
            companyId,
            BusinessProfile(
                businessProfileId = "", companyId = companyId, businessName = "Apex Traders", legalName = "Apex Traders Pvt Ltd",
                constitutionType = ConstitutionType.PRIVATE_LIMITED, gstin = "27AAAAA0000A1Z5", pan = "AAAAA0000A",
                tan = "ABCD12345E", udyam = "UDYAM-MH-01-0001234"
            )
        )
        assertTrue(result is AccountingResult.Success)

        val fetched = service.getBusinessProfile(companyId)!!
        assertEquals(ConstitutionType.PRIVATE_LIMITED, fetched.constitutionType)
        assertEquals("ABCD12345E", fetched.tan)
        assertEquals("UDYAM-MH-01-0001234", fetched.udyam)

        // Update (not just insert) also carries the new fields through correctly.
        service.upsertBusinessProfile(companyId, fetched.copy(constitutionType = ConstitutionType.LLP, tan = "WXYZ98765F"))
        val updated = service.getBusinessProfile(companyId)!!
        assertEquals(ConstitutionType.LLP, updated.constitutionType)
        assertEquals("WXYZ98765F", updated.tan)
        assertEquals("UDYAM-MH-01-0001234", updated.udyam) // untouched field survives the update
    }

    @Test
    fun testBusinessProfile_defaultConstitutionType_isProprietorship() {
        val profile = BusinessProfile(businessProfileId = "", companyId = companyId, businessName = "Solo Trader")
        assertEquals(ConstitutionType.PROPRIETORSHIP, profile.constitutionType)
    }
}

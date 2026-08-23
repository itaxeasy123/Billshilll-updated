package com.example.accounting.application.profile

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.rendering.BusinessProfile
import com.example.accounting.domain.rendering.IndividualProfile

/**
 * Application-service layer for [BusinessProfile]/[IndividualProfile] management - every method
 * takes an explicit `contextCompanyId` (the caller's authenticated/authorized tenant) and asserts
 * it agrees with the resource's own `companyId` before delegating to [AccountingRepository],
 * throwing [TenantMismatchException] on any mismatch rather than silently returning null/wrong
 * data. This is purely an authorization/mapping layer - it never mutates ledger/journal/voucher
 * data itself and never recomputes anything [AccountingRepository]'s frozen Phase 7D functions
 * already own.
 */
class ProfileApplicationService(private val repository: AccountingRepository) {

    suspend fun getBusinessProfile(contextCompanyId: String): BusinessProfile? {
        val profile = repository.getBusinessProfile(contextCompanyId) ?: return null
        assertSameCompany(contextCompanyId, profile.companyId)
        return profile
    }

    suspend fun getIndividualProfile(contextCompanyId: String): IndividualProfile? {
        val profile = repository.getIndividualProfile(contextCompanyId) ?: return null
        assertSameCompany(contextCompanyId, profile.companyId)
        return profile
    }

    /** [profile] must already carry `companyId == contextCompanyId` - a caller attempting to
     * write a profile under a different company's context throws instead of the write silently
     * landing (or failing to land) under the wrong tenant. */
    suspend fun upsertBusinessProfile(contextCompanyId: String, profile: BusinessProfile): AccountingResult<BusinessProfile> {
        assertSameCompany(contextCompanyId, profile.companyId)
        return repository.upsertBusinessProfile(profile)
    }

    suspend fun upsertIndividualProfile(contextCompanyId: String, profile: IndividualProfile): AccountingResult<IndividualProfile> {
        assertSameCompany(contextCompanyId, profile.companyId)
        return repository.upsertIndividualProfile(profile)
    }

    /** Masked view for a generic summary list/log line - never the raw PAN/GSTIN/bank account
     * number. See [SensitiveDataMasker]. */
    suspend fun getBusinessProfileSummary(contextCompanyId: String): MaskedBusinessProfileSummary? =
        getBusinessProfile(contextCompanyId)?.toMaskedSummary()

    suspend fun getIndividualProfileSummary(contextCompanyId: String): MaskedIndividualProfileSummary? =
        getIndividualProfile(contextCompanyId)?.toMaskedSummary()

    private fun assertSameCompany(contextCompanyId: String, resourceCompanyId: String) {
        if (contextCompanyId != resourceCompanyId) {
            throw TenantMismatchException(contextCompanyId, resourceCompanyId)
        }
    }
}

/** A [BusinessProfile] with every sensitive field masked - safe to log or return from a generic
 * summary/list endpoint. Deliberately omits [BusinessProfile.bankIfsc]/[BusinessProfile.upiId]/
 * [BusinessProfile.termsAndConditions]/asset-id fields entirely (a summary, not a full export). */
data class MaskedBusinessProfileSummary(
    val businessProfileId: String,
    val companyId: String,
    val businessName: String,
    val gstinMasked: String,
    val panMasked: String,
    val bankAccountNumberMasked: String
)

data class MaskedIndividualProfileSummary(
    val individualProfileId: String,
    val companyId: String,
    val name: String,
    val panMasked: String
)

fun BusinessProfile.toMaskedSummary(): MaskedBusinessProfileSummary = MaskedBusinessProfileSummary(
    businessProfileId = businessProfileId,
    companyId = companyId,
    businessName = businessName,
    gstinMasked = SensitiveDataMasker.maskGstin(gstin),
    panMasked = SensitiveDataMasker.maskPan(pan),
    bankAccountNumberMasked = SensitiveDataMasker.maskBankAccountNumber(bankAccountNumber)
)

fun IndividualProfile.toMaskedSummary(): MaskedIndividualProfileSummary = MaskedIndividualProfileSummary(
    individualProfileId = individualProfileId,
    companyId = companyId,
    name = name,
    panMasked = SensitiveDataMasker.maskPan(pan)
)

/** Safe-for-logs representation - use this (never `.toString()` on the raw domain object, and
 * never string-interpolate `profile.pan`/`profile.gstin`/`profile.bankAccountNumber` directly)
 * anywhere a [BusinessProfile] might end up in a log line. */
fun BusinessProfile.toLogSafeString(): String =
    "BusinessProfile(businessProfileId=$businessProfileId, companyId=$companyId, businessName=$businessName, " +
        "gstin=${SensitiveDataMasker.maskGstin(gstin)}, pan=${SensitiveDataMasker.maskPan(pan)}, " +
        "bankAccountNumber=${SensitiveDataMasker.maskBankAccountNumber(bankAccountNumber)})"

fun IndividualProfile.toLogSafeString(): String =
    "IndividualProfile(individualProfileId=$individualProfileId, companyId=$companyId, name=$name, " +
        "pan=${SensitiveDataMasker.maskPan(pan)})"

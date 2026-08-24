package com.example.accounting.application.banking

import com.example.accounting.application.profile.TenantMismatchException
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.entity.BankUpiProfileEntity
import com.example.accounting.domain.banking.BankUpiProfile
import com.example.accounting.domain.banking.UpiMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Real persistence for [BankUpiProfile] (Phase 7G domain model, previously unpersisted; Phase 7J-B
 * adds the table). Tenant-asserted the same way [com.example.accounting.application.profile.ProfileApplicationService]
 * already is - reuses the existing [TenantMismatchException], never a duplicated exception type.
 * Pure metadata: nothing in this file reads or writes any Ledger/Voucher/JournalItem table, so no
 * ledger running balance can ever be affected by creating, editing, or deleting a profile here.
 */
class BankUpiProfileService(private val dao: AccountingDao) {

    fun list(companyId: String): Flow<List<BankUpiProfile>> =
        dao.getBankUpiProfilesForCompany(companyId).map { entities -> entities.map { it.toDomain() } }

    fun listForParty(companyId: String, partyId: String): Flow<List<BankUpiProfile>> =
        dao.getBankUpiProfilesForParty(companyId, partyId).map { entities -> entities.map { it.toDomain() } }

    suspend fun create(contextCompanyId: String, profile: BankUpiProfile): AccountingResult<BankUpiProfile> {
        assertSameCompany(contextCompanyId, profile.companyId)
        val now = System.currentTimeMillis()
        val entity = profile.toEntity().copy(
            bankUpiProfileId = profile.bankUpiProfileId.ifBlank { "BUP_${UUID.randomUUID().toString().take(8)}_${profile.companyId}" },
            createdAt = now,
            updatedAt = now
        )
        dao.insertBankUpiProfile(entity)
        return AccountingResult.Success(entity.toDomain())
    }

    suspend fun update(contextCompanyId: String, profile: BankUpiProfile): AccountingResult<BankUpiProfile> {
        assertSameCompany(contextCompanyId, profile.companyId)
        val existing = dao.getBankUpiProfileById(profile.companyId, profile.bankUpiProfileId)
            ?: return AccountingResult.Failure(AppError.ResourceNotFound("BankUpiProfile", profile.bankUpiProfileId))
        val updated = profile.toEntity().copy(createdAt = existing.createdAt, updatedAt = System.currentTimeMillis())
        dao.updateBankUpiProfile(updated)
        return AccountingResult.Success(updated.toDomain())
    }

    suspend fun delete(contextCompanyId: String, bankUpiProfileId: String): AccountingResult<Unit> {
        val existing = dao.getBankUpiProfileById(contextCompanyId, bankUpiProfileId)
            ?: return AccountingResult.Failure(AppError.ResourceNotFound("BankUpiProfile", bankUpiProfileId))
        assertSameCompany(contextCompanyId, existing.companyId)
        dao.deleteBankUpiProfile(contextCompanyId, bankUpiProfileId)
        return AccountingResult.Success(Unit)
    }

    private fun assertSameCompany(contextCompanyId: String, resourceCompanyId: String) {
        if (contextCompanyId != resourceCompanyId) throw TenantMismatchException(contextCompanyId, resourceCompanyId)
    }
}

private fun BankUpiProfile.toEntity(): BankUpiProfileEntity = BankUpiProfileEntity(
    bankUpiProfileId = bankUpiProfileId,
    companyId = companyId,
    partyId = partyId,
    bankName = bankName,
    accountHolderName = accountHolderName,
    accountNumber = accountNumber,
    ifscCode = ifscCode,
    branchName = branchName,
    upiId = upi?.upiId,
    upiPayeeName = upi?.payeeName ?: "",
    upiIsVerified = upi?.isVerified ?: false,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun BankUpiProfileEntity.toDomain(): BankUpiProfile = BankUpiProfile(
    bankUpiProfileId = bankUpiProfileId,
    companyId = companyId,
    partyId = partyId,
    bankName = bankName,
    accountHolderName = accountHolderName,
    accountNumber = accountNumber,
    ifscCode = ifscCode,
    branchName = branchName,
    upi = upiId?.let { UpiMetadata(upiId = it, payeeName = upiPayeeName, isVerified = upiIsVerified) },
    createdAt = createdAt,
    updatedAt = updatedAt
)

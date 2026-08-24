package com.example.accounting.application.automation

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.recurring.RecurringVoucherDraft
import com.example.accounting.domain.recurring.RecurringVoucherDraftStatus
import kotlinx.coroutines.flow.Flow

/**
 * Application-service facade for reviewing Recurring Voucher drafts (Phase 7J-B, "Voucher"/
 * automation scope) - a thin, pure-delegation wrapper over the existing, unmodified Phase 7F
 * functions [AccountingRepository.getRecurringVoucherDrafts]/[AccountingRepository.updateRecurringVoucherDraft]/
 * [AccountingRepository.discardRecurringVoucherDraft]/[AccountingRepository.postRecurringVoucherDraft].
 * [postDraft] never posts directly - it delegates to the one function anywhere that turns a
 * recurring draft into a real voucher, which itself only ever calls the existing, unmodified
 * `postVoucher`.
 */
class RecurringVoucherManagementService(private val repository: AccountingRepository) {

    fun getDrafts(companyId: String, status: RecurringVoucherDraftStatus = RecurringVoucherDraftStatus.PENDING_REVIEW): Flow<List<RecurringVoucherDraft>> =
        repository.getRecurringVoucherDrafts(companyId, status)

    suspend fun updateDraft(companyId: String, draft: RecurringVoucherDraft): AccountingResult<RecurringVoucherDraft> =
        repository.updateRecurringVoucherDraft(companyId, draft)

    suspend fun discardDraft(companyId: String, draftId: String): AccountingResult<Unit> =
        repository.discardRecurringVoucherDraft(companyId, draftId)

    suspend fun postDraft(companyId: String, draftId: String, postedBy: String = "SENIOR_ACCOUNTANT"): AccountingResult<Voucher> =
        repository.postRecurringVoucherDraft(companyId, draftId, postedBy)
}

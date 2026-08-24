package com.example.accounting.application.voucher

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.entity.VoucherDocumentReferenceEntity
import com.example.accounting.data.local.entity.VoucherDraftEntity
import com.example.accounting.data.local.entity.VoucherDraftLineEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.inventory.VoucherStockLine
import com.example.accounting.domain.taxation.gst.GstTransaction
import java.util.UUID

/**
 * Real implementation of [VoucherManagementService] (Phase 7J-B). Persists drafts to the new
 * `voucher_drafts`/`voucher_draft_lines` tables via [dao] directly - the same "owns its own
 * self-contained tables" pattern [AccountingRepository] itself already uses, since these two tables
 * are never read by `generateTrialBalance`/`generateBalanceSheet`/GST/inventory computation. The
 * frozen [VoucherManagementService] interface types [createDraft]/[editDraft]/[postDraft] as plain
 * [Voucher] (not a bespoke draft type) - this implementation treats the returned `Voucher.voucherId`
 * as the draft's own id while `PENDING_REVIEW` (never a real `vouchers` row until [postDraft]),
 * exactly mirroring how a caller round-trips it: `createDraft` hands back a `Voucher` whose
 * `voucherId` IS the draft id, and `postDraft` is expected to receive that same id back.
 * [postDraft] is a direct delegation to the existing, unmodified [AccountingRepository.postVoucher]
 * - never a second posting mechanism.
 */
class VoucherManagementServiceImpl(
    private val dao: AccountingDao,
    private val repository: AccountingRepository
) : VoucherManagementService {

    override suspend fun createDraft(voucher: Voucher): AccountingResult<Voucher> {
        if (voucher.items.isEmpty()) {
            return AccountingResult.Failure(AppError.ValidationError("A voucher draft must have at least one line."))
        }
        val draftId = voucher.voucherId.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        dao.insertVoucherDraft(
            VoucherDraftEntity(
                draftId = draftId,
                companyId = voucher.companyId,
                financialYearId = voucher.financialYearId,
                voucherType = voucher.voucherType,
                date = voucher.date.toString(),
                referenceNumber = voucher.referenceNumber,
                narration = voucher.narration,
                status = VoucherDraftStatus.PENDING_REVIEW,
                postedVoucherId = null,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.insertVoucherDraftLines(voucher.items.mapIndexed { index, item -> item.toDraftLineEntity(draftId, index) })
        return AccountingResult.Success(voucher.copy(voucherId = draftId, isPosted = false))
    }

    override suspend fun editDraft(draftVoucherId: String, voucher: Voucher): AccountingResult<Voucher> {
        val existing = dao.getVoucherDraftById(voucher.companyId, draftVoucherId)
            ?: return AccountingResult.Failure(AppError.ResourceNotFound("VoucherDraft", draftVoucherId))
        if (existing.status != VoucherDraftStatus.PENDING_REVIEW) {
            return AccountingResult.Failure(AppError.ValidationError("Only a pending-review draft can be edited."))
        }
        if (voucher.items.isEmpty()) {
            return AccountingResult.Failure(AppError.ValidationError("A voucher draft must have at least one line."))
        }
        val now = System.currentTimeMillis()
        dao.updateVoucherDraft(
            existing.copy(
                date = voucher.date.toString(),
                referenceNumber = voucher.referenceNumber,
                narration = voucher.narration,
                updatedAt = now
            )
        )
        dao.deleteLinesForVoucherDraft(draftVoucherId)
        dao.insertVoucherDraftLines(voucher.items.mapIndexed { index, item -> item.toDraftLineEntity(draftVoucherId, index) })
        return AccountingResult.Success(voucher.copy(voucherId = draftVoucherId, isPosted = false))
    }

    override suspend fun postDraft(
        voucher: Voucher,
        idempotencyKey: String,
        stockLines: List<VoucherStockLine>,
        gstTransactions: List<GstTransaction>
    ): AccountingResult<Voucher> {
        val postResult = repository.postVoucher(voucher, idempotencyKey, stockLines, gstTransactions)
        if (postResult is AccountingResult.Failure) return postResult
        val posted = (postResult as AccountingResult.Success).data

        // If this voucher originated from a draft (voucher.voucherId == draftId), mark it POSTED -
        // never required: postDraft is also legal to call directly (matching postVoucher's own
        // signature) with no prior draft at all.
        val existingDraft = dao.getVoucherDraftById(voucher.companyId, voucher.voucherId)
        if (existingDraft != null && existingDraft.status == VoucherDraftStatus.PENDING_REVIEW) {
            dao.updateVoucherDraft(
                existingDraft.copy(status = VoucherDraftStatus.POSTED, postedVoucherId = posted.voucherId, updatedAt = System.currentTimeMillis())
            )
        }
        return AccountingResult.Success(posted)
    }

    override suspend fun attachDocumentReference(companyId: String, voucherId: String, documentAssetId: String): AccountingResult<Unit> {
        dao.insertVoucherDocumentReference(
            VoucherDocumentReferenceEntity(
                referenceId = UUID.randomUUID().toString(),
                companyId = companyId,
                voucherId = voucherId,
                documentAssetId = documentAssetId,
                createdAt = System.currentTimeMillis()
            )
        )
        return AccountingResult.Success(Unit)
    }

    private fun JournalItem.toDraftLineEntity(draftId: String, index: Int): VoucherDraftLineEntity = VoucherDraftLineEntity(
        draftLineId = itemId.ifBlank { UUID.randomUUID().toString() },
        draftId = draftId,
        ledgerId = ledgerId,
        type = type,
        amountPaise = amount.paise,
        narration = narration,
        lineOrder = if (lineOrder != 0) lineOrder else index + 1
    )

    /** Discards a still-pending draft - terminal, the row is kept (audit trail), never re-created
     * under the same [draftId]. Not part of the frozen [VoucherManagementService] interface (which
     * only names create/edit/post/attach) - an additive convenience matching
     * [com.example.accounting.application.automation.RecurringVoucherManagementService.discardDraft]'s
     * equivalent for the recurring-voucher draft table. */
    suspend fun discardDraft(companyId: String, draftId: String): AccountingResult<Unit> {
        val existing = dao.getVoucherDraftById(companyId, draftId)
            ?: return AccountingResult.Failure(AppError.ResourceNotFound("VoucherDraft", draftId))
        if (existing.status != VoucherDraftStatus.PENDING_REVIEW) {
            return AccountingResult.Failure(AppError.ValidationError("Only a pending-review draft can be discarded."))
        }
        dao.updateVoucherDraft(existing.copy(status = VoucherDraftStatus.DISCARDED, updatedAt = System.currentTimeMillis()))
        return AccountingResult.Success(Unit)
    }
}

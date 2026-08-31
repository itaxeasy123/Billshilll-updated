package com.example.accounting.application.voucher

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.dao.VoucherAttachmentRow
import com.example.accounting.data.local.entity.VoucherDocumentReferenceEntity
import com.example.accounting.data.local.entity.VoucherDraftEntity
import com.example.accounting.data.local.entity.VoucherDraftLineEntity
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.JournalItem
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.inventory.VoucherStockLine
import com.example.accounting.domain.taxation.gst.GstTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
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

    /**
     * Phase 7J-B.2 hardening - the original version trusted the caller entirely (no existence or
     * company checks). Now verifies, in order: (1) the voucher exists for [companyId] - since
     * [AccountingDao.getVoucherById] is itself `companyId`-scoped, a voucherId that belongs to a
     * *different* company also fails here, not just a genuinely missing voucherId; (2) the document
     * asset exists for the same [companyId], by the same company-scoped-query argument. Both checks
     * together are what makes cross-company attachment structurally impossible, not just
     * UI-discouraged. Duplicate `(voucherId, documentAssetId)` is idempotent, not an error: if the
     * exact pair is already linked, this returns Success with the existing reference untouched
     * (never a second row, never a changed `referenceId`/`createdAt`) - safe to call twice from a
     * retried UI action. The `(voucherId, documentAssetId)` unique index (`MIGRATION_16_17`) is the
     * DB-level backstop for this same guarantee, in case of a race between the pre-check and insert.
     */
    override suspend fun attachDocumentReference(companyId: String, voucherId: String, documentAssetId: String): AccountingResult<Unit> {
        dao.getVoucherById(companyId, voucherId)
            ?: return AccountingResult.Failure(AppError.ValidationError("Voucher '$voucherId' was not found for this company."))
        dao.getDocumentAssetById(companyId, documentAssetId)
            ?: return AccountingResult.Failure(AppError.ValidationError("Document asset '$documentAssetId' was not found for this company."))

        val alreadyAttached = dao.getDocumentReferencesForVoucher(companyId, voucherId)
            .any { it.documentAssetId == documentAssetId }
        if (alreadyAttached) return AccountingResult.Success(Unit)

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

    /**
     * Removes one voucher attachment (Phase 7J-B.2) - unlink only. Deletes exactly the
     * `voucher_document_references` row identified by [referenceId], scoped to [companyId]. Never
     * deletes the [com.example.accounting.domain.rendering.DocumentAsset] row or its underlying
     * file (the same asset may still be referenced by another voucher, or re-attached later), never
     * touches the voucher or its journal items. Not part of the frozen [VoucherManagementService]
     * interface - an additive convenience, matching [discardDraft]'s precedent.
     */
    suspend fun removeDocumentReference(companyId: String, referenceId: String): AccountingResult<Unit> {
        val deletedRows = dao.deleteVoucherDocumentReference(companyId, referenceId)
        if (deletedRows == 0) {
            return AccountingResult.Failure(AppError.ResourceNotFound("VoucherDocumentReference", referenceId))
        }
        return AccountingResult.Success(Unit)
    }

    /** Read-only, joined attachment listing (Phase 7J-B.2) for a future attachments UI - an
     * additive convenience, not part of the frozen [VoucherManagementService] interface, mirroring
     * [listDrafts]'s precedent of exposing a read helper beyond the frozen contract. */
    suspend fun getAttachmentsForVoucher(companyId: String, voucherId: String): List<VoucherAttachmentRow> =
        dao.getVoucherAttachments(companyId, voucherId)

    /** Read-only draft listing (Phase 7J UI) - not part of the frozen [VoucherManagementService]
     * interface, an additive convenience mirroring
     * [com.example.accounting.application.automation.RecurringVoucherManagementService.getDrafts]'s
     * equivalent for the recurring-voucher draft table. Fetches each draft's lines via a suspend
     * call inside [kotlinx.coroutines.flow.map] - never a second reactive source, never recomputes
     * a balance. */
    fun listDrafts(companyId: String, status: VoucherDraftStatus): Flow<List<VoucherDraft>> =
        dao.getVoucherDraftsByStatus(companyId, status).map { entities ->
            entities.map { entity -> entity.toDomain(dao.getLinesForVoucherDraft(entity.draftId).map { it.toDomain() }) }
        }

    private fun VoucherDraftEntity.toDomain(lines: List<VoucherDraftLine>): VoucherDraft = VoucherDraft(
        draftId = draftId, companyId = companyId, financialYearId = financialYearId,
        voucherType = voucherType, date = LocalDate.parse(date), referenceNumber = referenceNumber,
        narration = narration, lines = lines, status = status, postedVoucherId = postedVoucherId,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun VoucherDraftLineEntity.toDomain(): VoucherDraftLine = VoucherDraftLine(
        ledgerId = ledgerId, type = type, amountPaise = amountPaise, narration = narration, lineOrder = lineOrder
    )

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

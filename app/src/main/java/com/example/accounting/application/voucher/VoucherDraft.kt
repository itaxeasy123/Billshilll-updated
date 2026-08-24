package com.example.accounting.application.voucher

import com.example.accounting.core.common.DrCr
import com.example.accounting.domain.accounting.VoucherType
import java.time.LocalDate

/**
 * A draft's lifecycle (Phase 7J-B): [PENDING_REVIEW] (just created, awaiting the user),
 * [POSTED] (the user posted it - [VoucherDraft.postedVoucherId] is then set), or [DISCARDED] (the
 * user rejected it). Mirrors [com.example.accounting.domain.recurring.RecurringVoucherDraftStatus]'s
 * exact three-state shape.
 */
enum class VoucherDraftStatus { PENDING_REVIEW, POSTED, DISCARDED }

data class VoucherDraftLine(
    val ledgerId: String,
    val type: DrCr,
    val amountPaise: Long,
    val narration: String = "",
    val lineOrder: Int = 0
)

/**
 * A review-only candidate voucher (Phase 7J-B) - the new draft concept [VoucherManagementService]'s
 * own doc comment flagged as needed, since a generic [com.example.accounting.domain.accounting.Voucher]
 * has no draft state today (`VoucherPostingEngine` posts atomically and immediately). Structurally
 * mirrors [com.example.accounting.domain.recurring.RecurringVoucherDraft]: deliberately **not** a
 * [com.example.accounting.domain.accounting.Voucher] and never stored in the `vouchers` table, so
 * "no journal/ledger/balance/GST/inventory effect until posted" is a structural guarantee, not a
 * convention. Created either directly by a user (via [VoucherManagementServiceImpl.createDraft]) or
 * from an OCR suggestion (via [com.example.accounting.application.ocr.OcrSuggestionService.reviewAndPrefillVoucherDraft]) -
 * either way, [VoucherManagementServiceImpl.postDraft] is the only function anywhere that turns one
 * into a real, posted voucher, and it does so by direct delegation to the existing, unmodified
 * `AccountingRepository.postVoucher` - never a second posting mechanism.
 */
data class VoucherDraft(
    val draftId: String,
    val companyId: String,
    val financialYearId: String,
    val voucherType: VoucherType,
    val date: LocalDate,
    val referenceNumber: String = "",
    val narration: String = "",
    val lines: List<VoucherDraftLine> = emptyList(),
    val status: VoucherDraftStatus = VoucherDraftStatus.PENDING_REVIEW,
    val postedVoucherId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

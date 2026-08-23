package com.example.accounting.application.voucher

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.inventory.VoucherStockLine
import com.example.accounting.domain.taxation.gst.GstTransaction
import java.util.UUID

/**
 * Application-service contract for Voucher management (Phase 7J-A) - an interface only, no
 * implementation, matching every 7H/7I/7J contract's "architecture before implementation"
 * discipline.
 *
 * **This is a materially bigger claim than [com.example.accounting.application.invoice.InvoiceManagementService]'s
 * equivalent, and it is flagged here rather than glossed over**: unlike
 * [com.example.accounting.domain.invoice.Invoice] (Phase 7A, already draft-capable - zero
 * accounting effect until `postInvoice` sets its `voucherId`), a generic [Voucher] has **no draft
 * concept today**. `VoucherPostingEngine`/`AccountingRepository.postVoucher` post atomically and
 * immediately - there is no "create a voucher, review it, post it later" state anywhere in the
 * frozen posting path, which is exactly why Phase 7F had to build a whole new
 * `RecurringVoucherDraftEntity` table from scratch for recurring vouchers rather than reusing
 * anything Voucher itself already provided. A future implementation of [createDraft]/[editDraft]
 * therefore needs its own new draft entity, mirroring `RecurringVoucherDraftEntity`'s exact
 * pattern (unique-indexed, zero ledger/journal effect until posted) - **not built here**. This
 * contract only defines the shape; it does not itself introduce a second posting mechanism, and a
 * future implementation must not either.
 *
 * [postDraft] mirrors `AccountingRepository.postVoucher`'s exact parameter shape - a future
 * implementation is a direct delegation to the existing, unmodified `VoucherPostingEngine` via
 * that function, never a parallel posting path.
 */
interface VoucherManagementService {

    /** Creates a new, unposted voucher draft. See the class doc - this requires a new draft
     * entity in a future implementation; `Voucher` itself carries no draft state today. */
    suspend fun createDraft(voucher: Voucher): AccountingResult<Voucher>

    /** Edits a still-unposted draft identified by [draftVoucherId]. Never callable once posted -
     * posting is immutable, matching every other voucher's Deletion Policy in this app (a posted
     * voucher is corrected via a compensating reversal through `deleteVoucherSafely`, never an
     * in-place edit). */
    suspend fun editDraft(draftVoucherId: String, voucher: Voucher): AccountingResult<Voucher>

    /** Mirrors `AccountingRepository.postVoucher`'s exact signature - a future implementation is a
     * direct delegation to the existing, unmodified `VoucherPostingEngine`/`DoubleEntryValidator`
     * path, no new logic. */
    suspend fun postDraft(
        voucher: Voucher,
        idempotencyKey: String = UUID.randomUUID().toString(),
        stockLines: List<VoucherStockLine> = emptyList(),
        gstTransactions: List<GstTransaction> = emptyList()
    ): AccountingResult<Voucher>

    /** Links an already-uploaded [com.example.accounting.domain.rendering.DocumentAsset]
     * (referenced by [documentAssetId], never duplicated here - the same reference-by-id
     * convention every 7H/7I/7J contract already uses) to an existing voucher as supporting
     * evidence (e.g. a scanned receipt/bill image) - metadata only, never an accounting mutation;
     * it does not create, edit, or affect the voucher's journal/ledger effect in any way. */
    suspend fun attachDocumentReference(companyId: String, voucherId: String, documentAssetId: String): AccountingResult<Unit>
}

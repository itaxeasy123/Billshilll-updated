package com.example.accounting.application.invoice

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.domain.invoice.Invoice
import com.example.accounting.domain.invoice.InvoiceLine
import com.example.accounting.domain.invoice.InvoiceStatus
import java.time.LocalDate

/**
 * Search/filter configuration for [InvoiceManagementService.search] - every field optional/empty-
 * default so "no filter" is the natural "no arguments changed" state. [state] reuses the existing,
 * frozen [InvoiceStatus] (DRAFT/POSTED/PARTIALLY_PAID/PAID/OVERDUE/CANCELLED, always derived by
 * `InvoiceStatusEngine`, never a second stored status this filter could drift from). [dateRange]
 * matches the existing `AccountingRepository.generateDayBook`'s `ClosedRange<LocalDate>`
 * convention rather than inventing a new from/to pair shape.
 */
data class InvoiceFilter(
    val query: String = "",
    val state: InvoiceStatus? = null,
    val dateRange: ClosedRange<LocalDate>? = null,
    val partyId: String? = null
)

/**
 * Application-service contract for Invoice management (Phase 7J-A) - an interface only, no
 * implementation, matching every 7H/7I/7J contract's "architecture before implementation"
 * discipline. Unlike [com.example.accounting.application.profile.ProfileApplicationService]
 * (already a concrete class, since Profile's underlying capability needed only a thin tenant-
 * assertion wrapper), Invoice's real capability is richer - creating/updating a draft can require
 * re-deriving GST/stock/journal facts - so this phase defines the contract shape first; a future
 * phase provides the implementation.
 *
 * **Absolute rule**: this layer must never calculate an accounting fact itself. Every method that
 * would need journal lines, stock lines, or GST-transaction facts (an edited [updateDraft], a
 * [duplicateInvoice] copy) must orchestrate the existing, unmodified
 * [com.example.accounting.domain.trading.TradingWorkflowEngine] (`buildSale`/`buildPurchase`/
 * `buildNote`) for that computation, then hand the result to the existing, unmodified
 * [com.example.accounting.data.repository.AccountingRepository] functions (`createDraftInvoice`,
 * `postInvoice`, `cancelInvoice` - [cancelInvoice] here matches that function's exact signature so
 * a future implementation is a direct delegation) - exactly the same computation/posting path
 * every other invoice in this app already goes through. No UI component may implement or be
 * implemented by this interface.
 */
interface InvoiceManagementService {

    /** Mirrors `AccountingRepository.createDraftInvoice`'s signature exactly - a future
     * implementation is a direct delegation, no new logic. */
    suspend fun createDraft(invoice: Invoice, lines: List<InvoiceLine>): AccountingResult<Invoice>

    /** Updates an existing, still-DRAFT invoice's header/lines - [invoice] must carry the
     * `invoiceId` being updated. Never callable on an already-posted invoice (posting is
     * immutable, matching every other voucher's Deletion Policy in this app) - a future
     * implementation rejects with the existing `AppError.BusinessRuleViolation` shape, the same
     * way `postInvoice` already rejects double-posting. */
    suspend fun updateDraft(invoice: Invoice, lines: List<InvoiceLine>): AccountingResult<Invoice>

    /** Creates a new DRAFT invoice by copying [sourceInvoiceId]'s header/lines (a new `invoiceId`
     * and its own independently-generated `invoiceNumber`, never the source's) - never a
     * reference/link back to the source the way [Invoice.referenceInvoiceId] represents a
     * Credit/Debit Note relationship; a duplicate is a fresh, unrelated draft. */
    suspend fun duplicateInvoice(companyId: String, sourceInvoiceId: String): AccountingResult<Invoice>

    /** Mirrors `AccountingRepository.cancelInvoice`'s exact signature - a future implementation is
     * a direct delegation, no new logic. */
    suspend fun cancelInvoice(companyId: String, financialYearId: String, invoiceId: String): AccountingResult<Unit>

    /** Read-only. Never recomputes [InvoiceStatus] itself - a future implementation still derives
     * it via the existing, frozen `InvoiceStatusEngine`/`computeOutstandingPaise`, exactly like
     * `AccountingRepository.generateOutstandingReport` already does; this only filters/searches
     * already-derived results. */
    suspend fun search(companyId: String, filter: InvoiceFilter): AccountingResult<List<Invoice>>
}

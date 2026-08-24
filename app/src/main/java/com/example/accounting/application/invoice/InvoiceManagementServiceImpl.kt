package com.example.accounting.application.invoice

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.invoice.Invoice
import com.example.accounting.domain.invoice.InvoiceLine
import kotlinx.coroutines.flow.first

/**
 * Real implementation of [InvoiceManagementService] (Phase 7J-B) - every method is a direct
 * delegation to the existing, unmodified [AccountingRepository] functions
 * ([AccountingRepository.createDraftInvoice], [AccountingRepository.updateDraftInvoice],
 * [AccountingRepository.cancelInvoice], [AccountingRepository.getInvoiceStatus],
 * [AccountingRepository.getInvoicesForParty]/[AccountingRepository.getInvoicesForCompany]).
 * [duplicateInvoice] never calculates a GST/journal fact itself - it only copies already-persisted
 * header/line data and re-submits it through the existing [AccountingRepository.createDraftInvoice],
 * exactly the same path any other draft creation already goes through.
 */
class InvoiceManagementServiceImpl(private val repository: AccountingRepository) : InvoiceManagementService {

    override suspend fun createDraft(invoice: Invoice, lines: List<InvoiceLine>): AccountingResult<Invoice> =
        repository.createDraftInvoice(invoice, lines)

    override suspend fun updateDraft(invoice: Invoice, lines: List<InvoiceLine>): AccountingResult<Invoice> =
        repository.updateDraftInvoice(invoice, lines)

    override suspend fun duplicateInvoice(companyId: String, sourceInvoiceId: String): AccountingResult<Invoice> {
        val source = repository.getInvoicesForCompany(companyId).first().firstOrNull { it.invoiceId == sourceInvoiceId }
            ?: return AccountingResult.Failure(AppError.ValidationError("Invoice '$sourceInvoiceId' was not found."))

        val linesResult = repository.getInvoiceLines(companyId, sourceInvoiceId)
        if (linesResult is AccountingResult.Failure) return linesResult
        val sourceLines = (linesResult as AccountingResult.Success).data

        // A fresh, unrelated draft (per the interface's own contract) - never a link back to the
        // source, never assumed to already have a due date, voucher, or trade-document origin.
        val newInvoice = source.copy(
            invoiceId = "", invoiceNumber = null, dueDate = null, voucherId = null,
            referenceInvoiceId = null, sourceTradeDocumentId = null,
            createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
        )
        val newLines = sourceLines.map { it.copy(lineId = "") }
        return repository.createDraftInvoice(newInvoice, newLines)
    }

    override suspend fun cancelInvoice(companyId: String, financialYearId: String, invoiceId: String): AccountingResult<Unit> =
        repository.cancelInvoice(companyId, financialYearId, invoiceId)

    override suspend fun search(companyId: String, filter: InvoiceFilter): AccountingResult<List<Invoice>> {
        val source = if (filter.partyId != null) {
            repository.getInvoicesForParty(companyId, filter.partyId).first()
        } else {
            repository.getInvoicesForCompany(companyId).first()
        }

        val filtered = source.filter { invoice ->
            val matchesQuery = filter.query.isBlank() ||
                invoice.narration.contains(filter.query, ignoreCase = true) ||
                (invoice.invoiceNumber?.contains(filter.query, ignoreCase = true) == true)
            val matchesDateRange = filter.dateRange == null ||
                (!invoice.date.isBefore(filter.dateRange.start) && !invoice.date.isAfter(filter.dateRange.endInclusive))
            matchesQuery && matchesDateRange
        }

        if (filter.state == null) return AccountingResult.Success(filtered)

        // Status is never recomputed here - always re-derived via the existing, frozen
        // InvoiceStatusEngine through getInvoiceStatus, exactly as the interface requires.
        val statusFiltered = filtered.filter { invoice ->
            val statusResult = repository.getInvoiceStatus(companyId, invoice.invoiceId)
            statusResult is AccountingResult.Success && statusResult.data == filter.state
        }
        return AccountingResult.Success(statusFiltered)
    }
}

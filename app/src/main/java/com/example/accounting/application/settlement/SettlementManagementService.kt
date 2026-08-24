package com.example.accounting.application.settlement

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.Money
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.trading.OutstandingInvoice

/**
 * Application-service facade for Receipt/Payment settlement allocation and outstanding lookup
 * (Phase 7J-B, "Settlement/Outstanding") - a thin, pure-delegation wrapper over the existing,
 * unmodified [AccountingRepository.allocateSettlement]/[AccountingRepository.getOutstandingInvoices].
 * `Receipt`/`Payment` are [com.example.accounting.domain.accounting.VoucherType] values posted
 * through [com.example.accounting.application.voucher.VoucherManagementService] like any other
 * voucher - this service only covers the allocation step, never posting. Every method takes an
 * explicit `companyId`/`financialYearId` - never an implicit current company/FY.
 */
class SettlementManagementService(private val repository: AccountingRepository) {

    suspend fun allocateSettlement(
        companyId: String,
        financialYearId: String,
        settlementVoucherId: String,
        allocations: List<Pair<String, Money>>,
        unallocatedAmount: Money
    ): AccountingResult<Unit> =
        repository.allocateSettlement(companyId, financialYearId, settlementVoucherId, allocations, unallocatedAmount)

    suspend fun getOutstandingInvoices(companyId: String, partyLedgerId: String): List<OutstandingInvoice> =
        repository.getOutstandingInvoices(companyId, partyLedgerId)
}

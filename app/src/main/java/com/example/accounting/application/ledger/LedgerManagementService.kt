package com.example.accounting.application.ledger

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.Ledger
import kotlinx.coroutines.flow.Flow

/**
 * Application-service facade for Ledger management (Phase 7J-B, "Party/Ledger/Item") - a thin,
 * pure-delegation wrapper over the existing, unmodified [AccountingRepository.createLedger]/
 * [AccountingRepository.getLedgers]/[AccountingRepository.deleteLedgerSafely]. Never recomputes or
 * duplicates the underlying Suspense/System-ledger protection or deletion rules.
 */
class LedgerManagementService(private val repository: AccountingRepository) {

    suspend fun createLedger(ledger: Ledger): AccountingResult<Ledger> =
        repository.createLedger(ledger)

    fun getLedgers(companyId: String): Flow<List<Ledger>> =
        repository.getLedgers(companyId)

    suspend fun deleteLedgerSafely(companyId: String, ledgerId: String): AccountingResult<Unit> =
        repository.deleteLedgerSafely(companyId, ledgerId)
}

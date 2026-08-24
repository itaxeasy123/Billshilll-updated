package com.example.accounting.application.party

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.party.Party
import com.example.accounting.domain.party.PartyRole
import kotlinx.coroutines.flow.Flow

/**
 * Application-service facade for Party management (Phase 7J-B) - a thin, pure-delegation wrapper
 * over the existing, unmodified [AccountingRepository.createParty]/[AccountingRepository.getParties].
 * Exists so a future non-UI caller has a stable entry point independent of the repository's much
 * larger surface - never recomputes or duplicates the underlying logic. Every method takes an
 * explicit `companyId` (via [party]'s own field or as a direct parameter) - never an implicit
 * "current company" lookup.
 */
class PartyManagementService(private val repository: AccountingRepository) {

    suspend fun createParty(party: Party, ledgerTemplate: Ledger? = null): AccountingResult<Party> =
        repository.createParty(party, ledgerTemplate)

    fun getParties(companyId: String, role: PartyRole? = null): Flow<List<Party>> =
        repository.getParties(companyId, role)
}

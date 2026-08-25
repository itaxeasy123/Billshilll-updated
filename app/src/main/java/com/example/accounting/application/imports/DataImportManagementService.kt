package com.example.accounting.application.imports

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.dataimport.DataImportAdapter
import com.example.accounting.domain.dataimport.ImportFileFormat
import com.example.accounting.domain.dataimport.ImportReconciliationSummary
import com.example.accounting.domain.dataimport.ImportResult
import com.example.accounting.domain.dataimport.ImportRowOutcome
import com.example.accounting.domain.dataimport.ImportRowSuggestion
import com.example.accounting.domain.dataimport.ImportSuggestionType
import com.example.accounting.domain.inventory.StockItem
import com.example.accounting.domain.party.Party
import com.example.accounting.domain.party.PartyEntityType
import com.example.accounting.domain.party.PartyRole
import com.example.accounting.domain.rendering.BusinessProfile

/**
 * Application-service orchestration for the Import Draft/Suggestion/Review workflow (Phase 7J-B) -
 * [parseFile] is a thin pass-through to the injected [DataImportAdapter] (never re-implements
 * parsing). [reviewAndCreate] is the **only** function anywhere in this workflow that calls
 * [AccountingRepository.createParty]/[AccountingRepository.createLedger]/[AccountingRepository.createStockItem]
 * - always in direct response to an explicit human review action on one already-parsed
 * [ImportRowSuggestion], never automatically for a whole file. Kept on this service rather than the
 * adapter specifically to preserve the adapter's own zero-`AccountingRepository`-access guarantee.
 */
class DataImportManagementService(
    private val adapter: DataImportAdapter,
    private val repository: AccountingRepository
) {

    suspend fun parseFile(requestingCompany: BusinessProfile, format: ImportFileFormat, fileAssetId: String): AccountingResult<ImportResult> =
        adapter.parseFile(requestingCompany, format, fileAssetId)

    /** Creates exactly one Party/Ledger/StockItem from one already-reviewed suggestion's raw
     * [ImportRowSuggestion.fieldValues] - [resolvedType] is the human reviewer's own confirmed
     * choice (never trusted from [ImportRowSuggestion.suggestionType] alone, since that field is
     * itself only a best-effort guess). A missing required column is a structured
     * [AppError.ValidationError], never a guessed default. */
    suspend fun reviewAndCreate(companyId: String, suggestion: ImportRowSuggestion, resolvedType: ImportSuggestionType): AccountingResult<Any> {
        val fields = suggestion.fieldValues
        return when (resolvedType) {
            ImportSuggestionType.PARTY -> {
                val name = firstNonBlank(fields, "name", "partyname", "displayname", "party")
                    ?: return AccountingResult.Failure(AppError.ValidationError("Row ${suggestion.rowNumber}: could not find a party name column."))
                repository.createParty(
                    Party(
                        partyId = "",
                        companyId = companyId,
                        ledgerId = "",
                        role = if (firstNonBlank(fields, "role")?.uppercase() == "SUPPLIER") PartyRole.SUPPLIER else PartyRole.CUSTOMER,
                        entityType = PartyEntityType.BUSINESS,
                        displayName = name
                    )
                )
            }
            ImportSuggestionType.LEDGER -> {
                val name = firstNonBlank(fields, "name", "ledgername", "ledger")
                    ?: return AccountingResult.Failure(AppError.ValidationError("Row ${suggestion.rowNumber}: could not find a ledger name column."))
                val groupId = firstNonBlank(fields, "groupid", "group")
                    ?: return AccountingResult.Failure(AppError.ValidationError("Row ${suggestion.rowNumber}: could not find a group id column."))
                repository.createLedger(Ledger(ledgerId = "", companyId = companyId, groupId = groupId, name = name))
            }
            ImportSuggestionType.STOCK_ITEM -> {
                val name = firstNonBlank(fields, "name", "itemname", "item")
                    ?: return AccountingResult.Failure(AppError.ValidationError("Row ${suggestion.rowNumber}: could not find an item name column."))
                repository.createStockItem(
                    StockItem(
                        itemId = "",
                        companyId = companyId,
                        name = name,
                        sku = firstNonBlank(fields, "sku") ?: "",
                        hsnCode = firstNonBlank(fields, "hsn", "hsncode") ?: "",
                        gstRatePercent = firstNonBlank(fields, "gst", "gstrate", "gstratepercent")?.toDoubleOrNull() ?: 18.0
                    )
                )
            }
        }
    }

    /** Pure aggregation, direct delegation to [ImportReconciliationSummary.from] - never a second
     * computation of what was accepted/rejected/unresolved. [rowOutcomes] is the caller's own
     * record of each suggestion's review outcome (e.g. from repeated [reviewAndCreate] calls);
     * this service does not track outcomes itself. */
    fun summarize(result: ImportResult, rowOutcomes: Map<Int, ImportRowOutcome>): ImportReconciliationSummary =
        ImportReconciliationSummary.from(result, rowOutcomes)

    private fun firstNonBlank(fields: Map<String, String>, vararg keys: String): String? {
        val normalized = fields.mapKeys { it.key.lowercase().replace(" ", "").replace("_", "") }
        for (key in keys) {
            normalized[key]?.let { if (it.isNotBlank()) return it }
        }
        return null
    }
}

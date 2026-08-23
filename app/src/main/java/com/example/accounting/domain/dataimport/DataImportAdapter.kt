package com.example.accounting.domain.dataimport

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.domain.rendering.BusinessProfile

/** File formats this contract can parse. Deliberately mirrors the export side's
 * `domain.export.ExportFormat` naming so the two stay easy to reason about together, but this is
 * its own type - import and export are separate directions with separate boundaries. */
enum class ImportFileFormat { CSV, JSON, EXCEL }

/** Which kind of record one parsed row is a candidate for. Matches exactly the "Party/Ledger/Item"
 * grouping this phase audited - a Voucher is deliberately NOT importable as a bulk row type here;
 * bulk-importing double-entry transactions is a materially bigger safety question than importing
 * master data, and is explicitly out of scope until a future phase decides otherwise. */
enum class ImportSuggestionType { PARTY, LEDGER, STOCK_ITEM }

/**
 * One parsed row, turned into a suggestion - never a created record. [fieldValues] is
 * deliberately a generic, untyped `Map<String, String>` of whatever columns/keys the source file
 * actually had, rather than a fixed schema per [ImportSuggestionType] - real-world CSV/Excel
 * exports from other accounting tools vary too much in column naming to lock down a schema at the
 * architecture-only stage; a future implementation phase maps these raw values onto the real
 * [com.example.accounting.domain.party.Party]/[com.example.accounting.domain.accounting.Ledger]/
 * [com.example.accounting.domain.inventory.StockItem] fields and is where that mapping logic
 * belongs, not here.
 */
data class ImportRowSuggestion(
    val rowNumber: Int,
    val suggestionType: ImportSuggestionType,
    val fieldValues: Map<String, String>,
    val confidenceScore: Double,
    val validationWarnings: List<String> = emptyList()
)

/** Full result of one file-parsing pass - every row that produced a suggestion, and which row
 * numbers didn't parse at all (worth surfacing to the human, not silently dropping). */
data class ImportResult(
    val sourceFileName: String,
    val format: ImportFileFormat,
    val totalRowsParsed: Int,
    val suggestions: List<ImportRowSuggestion>,
    val unparsedRowNumbers: List<Int> = emptyList()
)

/**
 * Adapter boundary for CSV/JSON/Excel data import (Phase 7J - Management Architecture) - a pure
 * Kotlin interface only, no implementation, no CSV/Excel-parsing library dependency, no Android
 * dependency. Mirrors [com.example.accounting.domain.ocr.OcrIngestionAdapter]'s exact shape and
 * safety boundary: [parseFile] references an already-uploaded
 * [com.example.accounting.domain.rendering.DocumentAsset] by id (never re-modeling file storage
 * here) and returns [ImportResult] - a set of *suggestions*, never created records. Nothing
 * implementing this interface may create a [com.example.accounting.domain.party.Party],
 * [com.example.accounting.domain.accounting.Ledger], or
 * [com.example.accounting.domain.inventory.StockItem] directly - every suggestion is reviewed by
 * a human and created through the existing `AccountingRepository.createParty`/`createLedger`/
 * `createStockItem` functions, exactly like every other creation path in this app. There is no
 * bulk-import posting path, and none is planned by this contract - Voucher/transaction import is
 * explicitly out of scope (see [ImportSuggestionType]'s doc comment).
 */
interface DataImportAdapter {
    suspend fun parseFile(
        requestingCompany: BusinessProfile,
        format: ImportFileFormat,
        fileAssetId: String
    ): AccountingResult<ImportResult>
}

package com.example.accounting.data.dataimport

import com.example.accounting.core.common.AccountingResult
import com.example.accounting.core.common.AppError
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.domain.dataimport.DataImportAdapter
import com.example.accounting.domain.dataimport.ImportFileFormat
import com.example.accounting.domain.dataimport.ImportResult
import com.example.accounting.domain.dataimport.ImportRowSuggestion
import com.example.accounting.domain.dataimport.ImportSuggestionType
import com.example.accounting.domain.rendering.BusinessProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File

/**
 * Real CSV/JSON implementation of [DataImportAdapter] (Phase 7J-B) - lives in `data/`, not
 * `domain/`, since it needs actual file I/O (the same layering reason
 * `data/rendering/PdfDocumentRenderer.kt`/`ShareAdapter.kt` live in `data/`, Phase 7D). Reads an
 * already-uploaded [com.example.accounting.domain.rendering.DocumentAsset]'s file (via [dao], a
 * benign lookup read - **never** [com.example.accounting.data.repository.AccountingRepository],
 * verified by [Phase7JBDataImportTestSuite]'s reflection scan) and turns each row into an
 * [ImportRowSuggestion] - it never calls `createParty`/`createLedger`/`createStockItem` itself;
 * that human-review step lives exclusively on
 * [com.example.accounting.application.imports.DataImportManagementService.reviewAndCreate].
 * [ImportFileFormat.EXCEL] is not implemented (only CSV/JSON were greenlit for real I/O this phase)
 * - returns a structured failure, never a silently-empty result.
 */
class CsvJsonDataImportAdapter(private val dao: AccountingDao) : DataImportAdapter {

    private val moshi = Moshi.Builder().build()

    override suspend fun parseFile(
        requestingCompany: BusinessProfile,
        format: ImportFileFormat,
        fileAssetId: String
    ): AccountingResult<ImportResult> {
        val asset = dao.getDocumentAssetById(requestingCompany.companyId, fileAssetId)
            ?: return AccountingResult.Failure(AppError.ResourceNotFound("DocumentAsset", fileAssetId))
        val file = File(asset.storageReference)
        if (!file.exists()) {
            return AccountingResult.Failure(AppError.ValidationError("Import file '${asset.storageReference}' was not found on disk."))
        }

        return when (format) {
            ImportFileFormat.CSV -> AccountingResult.Success(parseCsv(file))
            ImportFileFormat.JSON -> parseJson(file)
            ImportFileFormat.EXCEL -> AccountingResult.Failure(AppError.ValidationError("Excel import is not yet implemented."))
        }
    }

    private fun parseCsv(file: File): ImportResult {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ImportResult(sourceFileName = file.name, format = ImportFileFormat.CSV, totalRowsParsed = 0, suggestions = emptyList())
        }
        val headers = splitCsvLine(lines.first())
        val suggestions = mutableListOf<ImportRowSuggestion>()
        val unparsed = mutableListOf<Int>()

        lines.drop(1).forEachIndexed { index, line ->
            val rowNumber = index + 2 // 1-indexed; row 1 is the header
            val cells = splitCsvLine(line)
            if (cells.size != headers.size) {
                unparsed.add(rowNumber)
                return@forEachIndexed
            }
            val fieldValues = headers.zip(cells).toMap()
            suggestions.add(
                ImportRowSuggestion(
                    rowNumber = rowNumber,
                    suggestionType = inferSuggestionType(fieldValues),
                    fieldValues = fieldValues,
                    confidenceScore = 1.0
                )
            )
        }
        return ImportResult(
            sourceFileName = file.name,
            format = ImportFileFormat.CSV,
            totalRowsParsed = lines.size - 1,
            suggestions = suggestions,
            unparsedRowNumbers = unparsed
        )
    }

    /** RFC-4180-ish: handles double-quoted fields containing commas - no external CSV library
     * dependency needed for this deliberately simple, well-scoped import format. */
    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { cells.add(current.toString().trim()); current.clear() }
                else -> current.append(c)
            }
        }
        cells.add(current.toString().trim())
        return cells
    }

    private fun parseJson(file: File): AccountingResult<ImportResult> {
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val listType = Types.newParameterizedType(List::class.java, mapType)
        val rawRows: List<Map<String, Any?>> = try {
            moshi.adapter<List<Map<String, Any?>>>(listType).fromJson(file.readText()) ?: emptyList()
        } catch (e: Exception) {
            return AccountingResult.Failure(AppError.ValidationError("Malformed JSON import file: ${e.message}"))
        }

        val suggestions = rawRows.mapIndexed { index, row ->
            val fieldValues = row.mapValues { (_, v) -> v?.toString() ?: "" }
            ImportRowSuggestion(
                rowNumber = index + 1,
                suggestionType = inferSuggestionType(fieldValues),
                fieldValues = fieldValues,
                confidenceScore = 1.0
            )
        }
        return AccountingResult.Success(
            ImportResult(sourceFileName = file.name, format = ImportFileFormat.JSON, totalRowsParsed = rawRows.size, suggestions = suggestions)
        )
    }

    /** Best-effort guess from the row's own column names - never a fixed schema (per
     * [ImportRowSuggestion]'s own doc comment, real-world exports vary too much in column naming).
     * Still only ever a *suggestion* the human reviewer confirms or corrects. */
    private fun inferSuggestionType(fieldValues: Map<String, String>): ImportSuggestionType {
        val keys = fieldValues.keys.map { it.lowercase() }
        return when {
            keys.any { it.contains("sku") || it.contains("hsn") || it.contains("stock") || it.contains("item") } -> ImportSuggestionType.STOCK_ITEM
            keys.any { it.contains("ledger") || it.contains("group") } -> ImportSuggestionType.LEDGER
            else -> ImportSuggestionType.PARTY
        }
    }
}

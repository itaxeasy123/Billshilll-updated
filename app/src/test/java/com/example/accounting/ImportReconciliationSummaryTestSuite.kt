package com.example.accounting

import com.example.accounting.application.imports.DataImportManagementService
import com.example.accounting.data.dataimport.CsvJsonDataImportAdapter
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.domain.dataimport.ImportFileFormat
import com.example.accounting.domain.dataimport.ImportReconciliationSummary
import com.example.accounting.domain.dataimport.ImportResult
import com.example.accounting.domain.dataimport.ImportRowOutcome
import com.example.accounting.domain.dataimport.ImportRowSuggestion
import com.example.accounting.domain.dataimport.ImportSuggestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure aggregation tests for [ImportReconciliationSummary] - the smallest next dependency-safe
 * step recommended by the Phase 0->7J-B read-only audit: a reconciliation summary sitting on top
 * of the already-real CSV/JSON import path ([CsvJsonDataImportAdapter] +
 * [DataImportManagementService]), before any further import/export or GST-structure work. No DAO,
 * no Room, no coroutine - every input is a plain, already-produced [ImportResult].
 */
class ImportReconciliationSummaryTestSuite {

    private fun suggestion(rowNumber: Int) = ImportRowSuggestion(
        rowNumber = rowNumber, suggestionType = ImportSuggestionType.PARTY,
        fieldValues = mapOf("name" to "Row $rowNumber"), confidenceScore = 1.0
    )

    private fun result(suggestionRowNumbers: List<Int>, unparsedRowNumbers: List<Int> = emptyList()) = ImportResult(
        sourceFileName = "parties.csv",
        format = ImportFileFormat.CSV,
        totalRowsParsed = suggestionRowNumbers.size + unparsedRowNumbers.size,
        suggestions = suggestionRowNumbers.map(::suggestion),
        unparsedRowNumbers = unparsedRowNumbers
    )

    @Test
    fun testFrom_allRowsReviewed_mixedOutcomes_countsCorrectly() {
        val importResult = result(listOf(1, 2, 3, 4))
        val outcomes = mapOf(
            1 to ImportRowOutcome.CREATED,
            2 to ImportRowOutcome.CREATED,
            3 to ImportRowOutcome.FAILED,
            4 to ImportRowOutcome.SKIPPED
        )

        val summary = ImportReconciliationSummary.from(importResult, outcomes)

        assertEquals(4, summary.suggestedRowCount)
        assertEquals(2, summary.createdCount)
        assertEquals(1, summary.failedCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(0, summary.unresolvedCount)
        assertTrue(summary.unresolvedRowNumbers.isEmpty())
    }

    @Test
    fun testFrom_someRowsNotYetReviewed_reportedAsUnresolved_neverSilentlyIgnored() {
        val importResult = result(listOf(1, 2, 3))
        val outcomes = mapOf(1 to ImportRowOutcome.CREATED)

        val summary = ImportReconciliationSummary.from(importResult, outcomes)

        assertEquals(1, summary.createdCount)
        assertEquals(2, summary.unresolvedCount)
        assertEquals(listOf(2, 3), summary.unresolvedRowNumbers)
    }

    @Test
    fun testFrom_unparsedRows_neverCountedAsUnresolvedSuggestions_butBlockFullReconciliation() {
        val importResult = result(suggestionRowNumbers = listOf(1), unparsedRowNumbers = listOf(2, 3))
        val outcomes = mapOf(1 to ImportRowOutcome.CREATED)

        val summary = ImportReconciliationSummary.from(importResult, outcomes)

        assertEquals(1, summary.suggestedRowCount)
        assertEquals(0, summary.unresolvedCount)
        assertEquals(2, summary.unparsedRowCount)
        assertEquals(3, summary.totalRowsParsed)
        assertFalse("unparsed rows must block full reconciliation even with zero unresolved suggestions", summary.isFullyReconciled)
    }

    @Test
    fun testFrom_everyRowResolvedAndNoneUnparsed_isFullyReconciled() {
        val importResult = result(listOf(1, 2))
        val outcomes = mapOf(1 to ImportRowOutcome.CREATED, 2 to ImportRowOutcome.SKIPPED)

        val summary = ImportReconciliationSummary.from(importResult, outcomes)

        assertTrue(summary.isFullyReconciled)
    }

    @Test
    fun testFrom_emptyResult_isFullyReconciledWithZeroCounts() {
        val importResult = result(emptyList())

        val summary = ImportReconciliationSummary.from(importResult, emptyMap())

        assertEquals(0, summary.suggestedRowCount)
        assertTrue(summary.isFullyReconciled)
    }

    @Test
    fun testFrom_outcomeForRowNumberNotInResult_isIgnored() {
        // A caller passing a stale/unrelated outcome map must not corrupt this file's own counts.
        val importResult = result(listOf(1))
        val outcomes = mapOf(1 to ImportRowOutcome.CREATED, 99 to ImportRowOutcome.FAILED)

        val summary = ImportReconciliationSummary.from(importResult, outcomes)

        assertEquals(1, summary.createdCount)
        assertEquals(0, summary.failedCount)
    }

    @Test
    fun testDataImportManagementService_summarize_delegatesDirectlyToImportReconciliationSummary() {
        val adapter = CsvJsonDataImportAdapter(FakeAccountingDao())
        val repository = AccountingRepository(FakeAccountingDao(), db = null)
        val service = DataImportManagementService(adapter, repository)

        val importResult = result(listOf(1, 2))
        val outcomes = mapOf(1 to ImportRowOutcome.CREATED, 2 to ImportRowOutcome.FAILED)

        val summary = service.summarize(importResult, outcomes)

        assertEquals(ImportReconciliationSummary.from(importResult, outcomes), summary)
    }
}

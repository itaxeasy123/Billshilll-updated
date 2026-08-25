package com.example.accounting.domain.dataimport

/**
 * The structured outcome of a human review action on one [ImportRowSuggestion] -
 * [ImportReconciliationSummary.from] needs this shape (never a raw, free-text outcome string) to
 * answer the reconciliation questions a data-interchange step must be able to answer before it's
 * trusted: what was accepted, what was rejected, what the reviewer deliberately skipped, and -
 * derived, not stored - what still has no outcome at all.
 */
enum class ImportRowOutcome { CREATED, FAILED, SKIPPED }

/**
 * Reconciliation summary for one import file's review pass (Section 14 of this project's own
 * dependency-order discipline: reconciliation before further import/export work). Pure aggregation
 * only, over an already-produced [ImportResult] and the reviewer's own per-row outcome map -
 * exactly the same "consume engine/adapter output, never recompute it" rule this codebase already
 * enforces for reports (`ReportManagementService`) and GST (`GroupAggregationEngine`). No Room/
 * Android/`AccountingRepository` dependency; this type never reads or writes anything itself.
 *
 * Deliberately does NOT report a "duplicate" or "mapped" count: neither
 * [com.example.accounting.data.dataimport.CsvJsonDataImportAdapter] nor
 * [com.example.accounting.application.imports.DataImportManagementService] detects duplicates or
 * records a field-mapping decision anywhere today, so fabricating those categories here would be a
 * guessed fact, not a derived one - the same "documented extension point, never a fabricated
 * value" rule `docs/52_MANAGEMENT_ARCHITECTURE.md` already applied to `DocumentData.shipDate`. A
 * future phase that adds real duplicate detection to the import pipeline is what would make that
 * category meaningful here.
 */
data class ImportReconciliationSummary(
    val sourceFileName: String,
    val format: ImportFileFormat,
    val totalRowsParsed: Int,
    val unparsedRowCount: Int,
    val suggestedRowCount: Int,
    val createdCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val unresolvedCount: Int,
    val unresolvedRowNumbers: List<Int>
) {
    /** True only when every parsed row is accounted for - every suggestion reviewed one way or
     * another with nothing left pending, AND no row failed to parse in the first place. A source
     * file is never considered reconciled while either is non-zero. */
    val isFullyReconciled: Boolean
        get() = unresolvedCount == 0 && unparsedRowCount == 0

    companion object {
        /** [rowOutcomes] is keyed by [ImportRowSuggestion.rowNumber]; a suggestion with no entry
         * counts as unresolved, never silently ignored. Outcomes for row numbers that aren't in
         * [result]'s own suggestions (a caller passing stale data) are ignored - this function
         * only ever reports on rows [result] itself actually produced. */
        fun from(result: ImportResult, rowOutcomes: Map<Int, ImportRowOutcome>): ImportReconciliationSummary {
            val suggestionRowNumbers = result.suggestions.map { it.rowNumber }
            var created = 0
            var failed = 0
            var skipped = 0
            val unresolvedRowNumbers = mutableListOf<Int>()

            for (rowNumber in suggestionRowNumbers) {
                when (rowOutcomes[rowNumber]) {
                    ImportRowOutcome.CREATED -> created++
                    ImportRowOutcome.FAILED -> failed++
                    ImportRowOutcome.SKIPPED -> skipped++
                    null -> unresolvedRowNumbers += rowNumber
                }
            }

            return ImportReconciliationSummary(
                sourceFileName = result.sourceFileName,
                format = result.format,
                totalRowsParsed = result.totalRowsParsed,
                unparsedRowCount = result.unparsedRowNumbers.size,
                suggestedRowCount = result.suggestions.size,
                createdCount = created,
                failedCount = failed,
                skippedCount = skipped,
                unresolvedCount = unresolvedRowNumbers.size,
                unresolvedRowNumbers = unresolvedRowNumbers.sorted()
            )
        }
    }
}

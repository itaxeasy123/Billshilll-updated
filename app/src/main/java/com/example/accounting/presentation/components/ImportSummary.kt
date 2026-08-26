package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.accounting.domain.dataimport.ImportReconciliationSummary
import com.example.accounting.presentation.theme.Spacing

/**
 * Phase UI-03: display widget for [ImportReconciliationSummary] - pure presentation over an
 * already-computed summary (built by [com.example.accounting.application.imports.DataImportManagementService.summarize],
 * never recomputed here). Not yet wired into [com.example.accounting.presentation.features.datatools.DataToolsScreen] -
 * that screen's ViewModel state currently tracks each row's outcome as a free-text `String`
 * (`"Created"` / `"Failed: <message>"`), not the structured `ImportRowOutcome` this summary needs
 * as input; changing that state shape is a ViewModel change, not a missing-component gap, and is
 * out of scope for this component-only phase. This component is ready for that future wiring
 * without needing to change once it happens.
 */
@Composable
fun ImportSummaryView(summary: ImportReconciliationSummary, modifier: Modifier = Modifier) {
    SectionCard(
        modifier = modifier,
        title = "Import Summary",
        subtitle = summary.sourceFileName,
        elevated = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TableRow("Rows parsed", value = summary.totalRowsParsed.toString())
            if (summary.unparsedRowCount > 0) {
                TableRow("Could not be read", value = summary.unparsedRowCount.toString())
            }
            TableRow("Created", value = summary.createdCount.toString())
            if (summary.failedCount > 0) {
                TableRow("Failed", value = summary.failedCount.toString())
            }
            if (summary.skippedCount > 0) {
                TableRow("Skipped", value = summary.skippedCount.toString())
            }
            if (summary.unresolvedCount > 0) {
                TableRow("Still awaiting review", value = summary.unresolvedCount.toString(), emphasize = true)
            }
            StatusBadge(
                text = if (summary.isFullyReconciled) "Fully reviewed" else "Review incomplete",
                containerColor = if (summary.isFullyReconciled) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                contentColor = if (summary.isFullyReconciled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
        }
    }
}

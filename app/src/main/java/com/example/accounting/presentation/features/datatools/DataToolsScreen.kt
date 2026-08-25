package com.example.accounting.presentation.features.datatools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accounting.domain.dataimport.ImportFileFormat
import com.example.accounting.domain.dataimport.ImportResult
import com.example.accounting.domain.dataimport.ImportRowSuggestion
import com.example.accounting.domain.dataimport.ImportSuggestionType
import com.example.accounting.presentation.components.SectionCard

/**
 * Phase 7J UI: "Import & Scan" - CSV/JSON import and "Scan Receipt" (OCR), both strictly
 * File -> Parser -> Validation -> Draft/Suggestion -> User Review -> Explicit Create/Post per the
 * UX spec's Section 9/10. Nothing here ever calls `createParty`/`createLedger`/`createStockItem`/
 * `postVoucher` directly - only `AccountingViewModel.reviewAndCreateImportRow` (one suggestion at
 * a time, human-triggered) does, and OCR only ever produces a `PENDING_REVIEW` voucher draft for
 * the Money tab's review queue, never a posted voucher.
 */
@Composable
fun DataToolsScreen(
    lastImportResult: ImportResult?,
    lastImportRowOutcomes: Map<Int, String>,
    onPickCsvFile: () -> Unit,
    onPickJsonFile: () -> Unit,
    onReviewAndCreateRow: (ImportRowSuggestion, ImportSuggestionType) -> Unit,
    onPickReceiptPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Import & Scan", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))

        SectionCard(elevated = true, title = "Import Party/Ledger/Item data") {
            Text(
                "Pick a CSV or JSON file - every row becomes a suggestion for you to review before anything is created.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickCsvFile) { Text("Pick CSV") }
                OutlinedButton(onClick = onPickJsonFile) { Text("Pick JSON") }
            }
        }

        SectionCard(elevated = true, title = "Scan Receipt") {
            Text(
                "Scan a receipt or bill photo - fields are extracted as a suggestion for a Voucher Draft; nothing posts automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onPickReceiptPhoto) { Text("Scan Receipt") }
        }

        if (lastImportResult != null) {
            Text(
                "Review: ${lastImportResult.suggestions.size} suggestion(s) from ${lastImportResult.sourceFileName}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(lastImportResult.suggestions, key = { it.rowNumber }) { suggestion ->
                    ImportRowCard(suggestion, lastImportRowOutcomes[suggestion.rowNumber], onReviewAndCreateRow)
                }
            }
        }
    }
}

@Composable
private fun ImportRowCard(
    suggestion: ImportRowSuggestion,
    outcome: String?,
    onReviewAndCreateRow: (ImportRowSuggestion, ImportSuggestionType) -> Unit
) {
    SectionCard(title = "Row ${suggestion.rowNumber}", subtitle = "Suggested: ${suggestion.suggestionType.name}") {
        suggestion.fieldValues.entries.take(4).forEach { (key, value) ->
            Text("$key: $value", style = MaterialTheme.typography.bodySmall)
        }
        if (suggestion.validationWarnings.isNotEmpty()) {
            Text(suggestion.validationWarnings.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (outcome != null) {
            Text(outcome, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ImportSuggestionType.entries.forEach { type ->
                    OutlinedButton(onClick = { onReviewAndCreateRow(suggestion, type) }) { Text("Create as ${type.name}") }
                }
            }
        }
    }
}

package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.accounting.presentation.theme.Spacing

/**
 * Widget - a generic preview-card shell: title, a content slot for whatever the caller wants to
 * preview, and an optional Print/Export/Share action row wired to already-existing callbacks (the
 * exact three actions `DocumentPreviewService`/`ExportManagementService`/`ShareAdapter` already
 * support - never a new rendering/export mechanism of its own). Deliberately does NOT render an
 * Invoice/TradeDocument's actual line items/GST breakdown - that would require duplicating
 * `DocumentData`'s full shape, which is a real screen's worth of layout, not a small reusable
 * component; [content] is where a future Invoice-preview screen would put that. A caller with
 * nothing to preview yet should show [EmptyState] instead of this, never an empty [DocumentPreview].
 */
@Composable
fun DocumentPreview(
    title: String,
    modifier: Modifier = Modifier,
    onPrint: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    SectionCard(modifier = modifier, elevated = true) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Row {
                if (onPrint != null) AppIconButton(icon = Icons.Default.Print, contentDescription = "Print", onClick = onPrint)
                if (onExport != null) AppIconButton(icon = Icons.Default.FileDownload, contentDescription = "Export", onClick = onExport)
                if (onShare != null) AppIconButton(icon = Icons.Default.Share, contentDescription = "Share", onClick = onShare)
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            content()
        }
    }
}

package com.example.accounting.presentation.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Base component - a thin, theme-token-defaulted wrapper over Material3's own `IconButton`. Not
 * previously built because plain `IconButton` is already a generic Compose primitive; this exists
 * only to give [tint] a centralized default (`MaterialTheme.colorScheme.onSurfaceVariant`, this
 * app's already-established icon color for non-primary actions - `AppTopBar`'s search/profile
 * icons, etc.) instead of every call site re-deciding a color, per rule 5 ("all components use
 * centralized design tokens"). Never changes touch-target size or click behavior versus a raw
 * `IconButton`.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

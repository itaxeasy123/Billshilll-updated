package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Base component - a thin wrapper over Material3's `HorizontalDivider`, defaulting its color to
 * `MaterialTheme.colorScheme.outlineVariant` (this app has no separate "divider color" concept -
 * `outlineVariant` is the correct Material3 token for it) so a screen never picks its own gray.
 */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

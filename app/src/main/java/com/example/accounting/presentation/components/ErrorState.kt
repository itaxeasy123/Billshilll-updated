package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.accounting.presentation.theme.Spacing

/**
 * Base widget - the [EmptyState]/[LoadingState] counterpart for a genuine error. This app's
 * established feedback mechanism is the Snackbar (`AccountingViewModel.emitMessage`, per
 * `docs/52_MANAGEMENT_ARCHITECTURE.md`'s own "no Toast" rule) for transient failures - this
 * component is for the different, rarer case of a whole screen/section having nothing to show
 * because its own data failed to load, where a Snackbar alone would leave a blank screen behind
 * it. [onRetry] is optional since not every error is retryable from the UI alone.
 */
@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRetry != null) {
                ActionButton(text = "Retry", style = ActionButtonStyle.TEXT, onClick = onRetry)
            }
        }
    }
}

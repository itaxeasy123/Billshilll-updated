package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.accounting.presentation.theme.Spacing

/**
 * Phase UI-03: the one loading-indicator primitive - closes a genuine, verified gap (zero
 * `CircularProgressIndicator` usage existed anywhere in this app before this phase). Pure
 * presentation, no data access, no business logic - a screen decides *when* it's loading; this
 * component only ever renders that decision.
 */
@Composable
fun AppLoader(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier, color = MaterialTheme.colorScheme.primary)
}

/**
 * Full-area loading state (a screen's entire content area while its data hasn't arrived yet) -
 * the loading counterpart to [EmptyState]/[ErrorState]. [message] is optional so a caller can
 * either show a spinner alone or a spinner with business-language context ("Loading Trial
 * Balance...", never "Fetching TrialBalanceReport").
 */
@Composable
fun LoadingState(message: String? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppLoader()
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

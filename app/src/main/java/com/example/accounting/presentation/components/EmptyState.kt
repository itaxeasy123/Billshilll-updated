package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.accounting.presentation.theme.Spacing

/**
 * Phase UI-03: a generic "nothing here yet" block - consolidates the shape every screen with an
 * empty list already hand-rolls its own version of (`ReportsCenterScreen.EmptyReportState`, a
 * bare `Text` in other list screens). [message] is always business language ("No data yet for
 * this report", "No customers yet") - this component never decides wording itself. Pre-existing
 * per-screen empty-state text is not retrofitted in this pass (out of scope, matching
 * [com.example.accounting.presentation.theme.Spacing]'s own "new code only" precedent).
 */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

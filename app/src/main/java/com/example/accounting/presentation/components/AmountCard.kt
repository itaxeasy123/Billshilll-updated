package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.accounting.core.common.Money
import com.example.accounting.presentation.theme.Radius
import com.example.accounting.presentation.theme.Spacing

/**
 * Base widget - a minimal label + prominent [Amount] card, deliberately smaller/plainer than
 * [StatCard] (no icon, no click affordance, no colored icon tint) - for a context that just needs
 * "one figure, clearly labeled" without a Dashboard-style tile (e.g. a totals line in a list, a
 * confirmation summary). Not a duplicate of `StatCard`: that one is icon+title+amount+subtitle in
 * an elevated tile meant to be tapped; this one is outlined, static, and deliberately plainer.
 */
@Composable
fun AmountCard(label: String, amount: Money, modifier: Modifier = Modifier) {
    OutlinedCard(shape = Radius.shapeMd, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Amount(amount, style = MaterialTheme.typography.headlineSmall, emphasize = true)
        }
    }
}

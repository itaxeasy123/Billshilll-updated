package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.accounting.core.common.Money

/**
 * Phase 7J UI: a two-column label/value row primitive (report line items, summary rows) - pure
 * presentation, no state, no service access. [value] renders via [Amount] when [money] is
 * supplied, otherwise as plain text for a non-monetary value.
 */
@Composable
fun TableRow(
    label: String,
    value: String? = null,
    money: Money? = null,
    emphasize: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (money != null) {
            Amount(money, style = MaterialTheme.typography.bodyMedium, emphasize = emphasize)
        } else if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Phase 7J UI: a tappable account/ledger row primitive - title + subtitle on the left, an amount
 * on the right. Every new list screen showing an account balance (Party, Cash/Bank, Ledger
 * search-result) uses this instead of hand-rolling the same `Row` layout per screen.
 */
@Composable
fun LedgerRow(
    title: String,
    subtitle: String? = null,
    money: Money? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        onClick = onClick,
        title = title,
        subtitle = subtitle,
        trailing = money?.let { { Amount(it, emphasize = true, style = MaterialTheme.typography.titleSmall) } }
    ) {}
}

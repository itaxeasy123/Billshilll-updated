package com.example.accounting.presentation.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.accounting.core.common.Money

/**
 * Phase 7J UI: the one place every new screen displays a monetary value - a thin wrapper over the
 * existing, frozen `Money.formatPlain()` (Phase 4.5: no currency symbol, no digit grouping, exact
 * paise precision). Never reformats, truncates, or re-rounds what `formatPlain()` already
 * produces - this component adds only the shared monospace/weight/color presentation, not a
 * second formatting rule.
 */
@Composable
fun Amount(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    emphasize: Boolean = false,
    color: Color = LocalContentColor.current
) {
    Text(
        text = money.formatPlain(),
        modifier = modifier,
        style = style.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = if (emphasize) FontWeight.Bold else style.fontWeight
        ),
        color = color
    )
}

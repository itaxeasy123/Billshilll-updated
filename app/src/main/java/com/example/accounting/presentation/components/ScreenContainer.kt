package com.example.accounting.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.accounting.presentation.theme.Spacing

/**
 * Base component ("Container") - standardizes the horizontal screen padding most screens already
 * apply ad hoc via a literal `.padding(horizontal = 16.dp)` (`Spacing.md`). Purely a layout
 * convenience - carries no data, no repository access, no business logic. Existing screens are not
 * retrofitted in this pass (out of scope, matching every other token/component file's own stated
 * scope this phase).
 */
@Composable
fun ScreenContainer(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = Spacing.md), content = content)
}

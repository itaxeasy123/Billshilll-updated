package com.example.accounting.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Named responsive-width tokens (Phase UI-02) - formalizes the tablet/NavigationRail threshold
 * [MainAppScreen] already enforces (`maxWidth >= 600.dp`, unchanged value, verified live on a
 * tablet this session) into a shared constant, so a future screen with its own width-based
 * decision references the same threshold instead of a second hardcoded `600.dp` that could drift
 * out of sync with this one.
 */
object Breakpoints {
    val tablet = 600.dp
}

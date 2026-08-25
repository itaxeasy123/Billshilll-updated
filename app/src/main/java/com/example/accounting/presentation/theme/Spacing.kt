package com.example.accounting.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Phase 7J UI: named spacing tokens for every new screen - replaces raw `.dp` literals scattered
 * per-screen with one shared scale, matching the informal 8/16dp rhythm the pre-existing screens
 * already use by convention. Pre-existing screens are not retrofitted (out of scope, "preserve
 * existing UI"); every new screen from this phase onward should reference these instead of a
 * literal `.dp` value.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

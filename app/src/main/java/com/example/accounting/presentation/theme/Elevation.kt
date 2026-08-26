package com.example.accounting.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Named elevation tokens (Phase UI-02) - the `ElevatedCard`/`OutlinedCard` split [SectionCard]
 * already uses covers most of this app's surfaces via Material3's own component defaults; this
 * scale exists for the remaining call sites that set an explicit `tonalElevation`/shadow value by
 * hand (e.g. `AppTopBar`'s surface), so a new one has a named step to reach for instead of another
 * bare `.dp` literal. Pre-existing screens are not retrofitted, matching [Spacing]/[Radius]'s own
 * stated scope.
 */
object Elevation {
    val none = 0.dp
    val low = 1.dp
    val medium = 2.dp
    val high = 4.dp
    val highest = 8.dp
}

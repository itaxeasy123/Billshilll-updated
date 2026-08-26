package com.example.accounting.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Named corner-radius tokens (Phase UI-02) - formalizes the values already in de-facto use across
 * the app (8dp/10dp/12dp/14dp/20dp were each already independently hardcoded at multiple call
 * sites - `grep`-counted before adding this file, never guessed) into one shared scale, the same
 * "give the existing convention a name" approach [Spacing] already took. Pre-existing screens are
 * not retrofitted (out of scope, "preserve the existing working theme unless a concrete problem
 * exists") - every new screen from this point on should reach for these instead of a literal
 * `.dp` value passed straight to `RoundedCornerShape`.
 */
object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 14.dp
    val xl = 20.dp

    val shapeSm = RoundedCornerShape(sm)
    val shapeMd = RoundedCornerShape(md)
    val shapeLg = RoundedCornerShape(lg)
    val shapeXl = RoundedCornerShape(xl)
}

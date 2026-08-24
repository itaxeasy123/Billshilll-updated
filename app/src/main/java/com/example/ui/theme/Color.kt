package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==== Phase 7J UI: Royal Purple + Off-White visual system ====
// Royal Purple is the primary brand color (active nav, primary actions, section highlights,
// selected containers, status accents) per the user-supplied UX spec (Section 20). Off-White is
// the main background. Kept deliberately distinct from IndigoTax (the pre-existing GST accent
// below) so a GST badge never reads as a second "primary" color next to active navigation.
val RoyalPurple = Color(0xFF4C1D95)
val RoyalPurpleLight = Color(0xFF7C3AED)
val RoyalPurpleDark = Color(0xFF2E1065)
val RoyalPurpleContainer = Color(0xFFEDE9FE)
val RoyalPurpleOnContainer = Color(0xFF2E1065)

val OffWhite = Color(0xFFFAF9F6)
val OffWhiteSurface = Color(0xFFFFFFFF)
val OffWhiteSurfaceVariant = Color(0xFFF1EEF7)
val CharcoalText = Color(0xFF1F1B24)
val CharcoalOnSurfaceVariant = Color(0xFF4A4453)
val PurpleGrayOutline = Color(0xFFC9C2D6)

// Dark-mode counterparts - a near-black, purple-tinted surface set, never the old Navy/Blue scheme.
val DeepPurpleBackground = Color(0xFF16121C)
val DeepPurpleSurface = Color(0xFF221D2B)
val DeepPurpleSurfaceVariant = Color(0xFF362F42)
val OffWhiteOnDark = Color(0xFFF1EEF7)
val PurpleGrayOutlineDark = Color(0xFF564C67)

// ==== Status/semantic accents - unchanged by the Royal Purple swap, orthogonal to primary/background ====
val EmeraldCredit = Color(0xFF059669)
val EmeraldLight = Color(0xFF10B981)
val EmeraldContainer = Color(0xFFD1FAE5)

val CrimsonDebit = Color(0xFFDC2626)
val CrimsonLight = Color(0xFFEF4444)
val CrimsonContainer = Color(0xFFFEE2E2)

val AmberWarning = Color(0xFFD97706)
val AmberLight = Color(0xFFF59E0B)
val AmberContainer = Color(0xFFFEF3C7)

// GST/tax accent - deliberately nudged toward blue-teal, away from the new Royal Purple primary,
// so a GST badge never visually collides with active-navigation purple on the same screen.
val IndigoTax = Color(0xFF0E7490)
val IndigoLight = Color(0xFF06B6D4)
val IndigoContainer = Color(0xFFCFFAFE)

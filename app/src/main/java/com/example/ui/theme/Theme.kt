package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Phase 7J UI: Royal Purple + Off-White, both light and dark - built the same session so the app
// never presents two visually unrelated products depending on system theme.
private val DarkColorScheme =
  darkColorScheme(
    primary = RoyalPurpleLight,
    onPrimary = Color.White,
    primaryContainer = RoyalPurpleDark,
    onPrimaryContainer = RoyalPurpleContainer,
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = EmeraldContainer,
    tertiary = AmberLight,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = AmberContainer,
    background = DeepPurpleBackground,
    onBackground = OffWhiteOnDark,
    surface = DeepPurpleSurface,
    onSurface = OffWhiteOnDark,
    surfaceVariant = DeepPurpleSurfaceVariant,
    onSurfaceVariant = PurpleGrayOutline,
    error = CrimsonLight,
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = CrimsonContainer,
    outline = PurpleGrayOutlineDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = RoyalPurple,
    onPrimary = Color.White,
    primaryContainer = RoyalPurpleContainer,
    onPrimaryContainer = RoyalPurpleOnContainer,
    secondary = EmeraldCredit,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = AmberWarning,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFF78350F),
    background = OffWhite,
    onBackground = CharcoalText,
    surface = OffWhiteSurface,
    onSurface = CharcoalText,
    surfaceVariant = OffWhiteSurfaceVariant,
    onSurfaceVariant = CharcoalOnSurfaceVariant,
    error = CrimsonDebit,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFF7F1D1D),
    outline = PurpleGrayOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class SmartSheetExtendedColors(
  val healthy: Color,
  val warning: Color,
  val error: Color,
  val info: Color,
  val matchHighlight: Color,
  val monoSurface: Color,
)

val LocalSmartSheetColors = staticCompositionLocalOf {
  SmartSheetExtendedColors(
    healthy = StatusHealthy,
    warning = StatusWarning,
    error = StatusError,
    info = StatusInfo,
    matchHighlight = MatchHighlightLight,
    monoSurface = LightSurfaceVariant,
  )
}

private val LightColorScheme =
  lightColorScheme(
    primary = NavyPrimary,
    onPrimary = OnNavyPrimary,
    primaryContainer = NavyContainer,
    onPrimaryContainer = OnNavyContainer,
    secondary = TealSecondary,
    onSecondary = OnTealSecondary,
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnTealContainer,
    tertiary = SkyTertiary,
    onTertiary = OnSkyTertiary,
    tertiaryContainer = SkyContainer,
    onTertiaryContainer = OnSkyContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = StatusError,
    onError = Color.White,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
  )

val SmartSheetShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

@Composable
fun SmartSheetTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val extendedColors =
    if (darkTheme) {
      SmartSheetExtendedColors(
        healthy = Color(0xFF2DD4BF),
        warning = Color(0xFFFBBF24),
        error = Color(0xFFF87171),
        info = Color(0xFF60A5FA),
        matchHighlight = MatchHighlightDark,
        monoSurface = DarkSurfaceVariant,
      )
    } else {
      SmartSheetExtendedColors(
        healthy = StatusHealthy,
        warning = StatusWarning,
        error = StatusError,
        info = StatusInfo,
        matchHighlight = MatchHighlightLight,
        monoSurface = LightSurfaceVariant,
      )
    }

  CompositionLocalProvider(LocalSmartSheetColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = SmartSheetTypography,
      shapes = SmartSheetShapes,
      content = content,
    )
  }
}

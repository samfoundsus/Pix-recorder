package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
  LIGHT,
  DARK,
  SYSTEM
}

val FallbackDarkColorScheme: ColorScheme =
  darkColorScheme(
    primary = PixelRedPrimary,
    onPrimary = PixelRedOnPrimary,
    primaryContainer = PixelRedContainer,
    onPrimaryContainer = PixelRedOnContainer,
    secondary = PixelCoral,
    onSecondary = FallbackDarkBackground,
    secondaryContainer = Color(0xFF5C1E1B),
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = PixelBlue,
    onTertiary = FallbackDarkBackground,
    tertiaryContainer = Color(0xFF00497E),
    onTertiaryContainer = Color(0xFFD1E4FF),
    background = FallbackDarkBackground,
    onBackground = FallbackDarkOnBackground,
    surface = FallbackDarkSurface,
    onSurface = FallbackDarkOnSurface,
    surfaceVariant = FallbackDarkSurfaceVariant,
    onSurfaceVariant = FallbackDarkOnSurfaceVariant,
    surfaceContainerLowest = FallbackDarkSurfaceContainerLowest,
    surfaceContainerLow = FallbackDarkSurfaceContainerLow,
    surfaceContainer = FallbackDarkSurfaceContainer,
    surfaceContainerHigh = FallbackDarkSurfaceContainerHigh,
    surfaceContainerHighest = FallbackDarkSurfaceContainerHighest,
    outline = FallbackDarkOutline,
    outlineVariant = FallbackDarkOutlineVariant
  )

val FallbackLightColorScheme: ColorScheme =
  lightColorScheme(
    primary = PixelRedLightPrimary,
    onPrimary = PixelRedLightOnPrimary,
    primaryContainer = PixelRedLightContainer,
    onPrimaryContainer = PixelRedLightOnContainer,
    secondary = PixelCoral,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD4),
    onSecondaryContainer = Color(0xFF3D0707),
    tertiary = PixelBlue,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1E4FF),
    onTertiaryContainer = Color(0xFF001D36),
    background = FallbackLightBackground,
    onBackground = FallbackLightOnBackground,
    surface = FallbackLightSurface,
    onSurface = FallbackLightOnSurface,
    surfaceVariant = FallbackLightSurfaceVariant,
    onSurfaceVariant = FallbackLightOnSurfaceVariant,
    surfaceContainerLowest = FallbackLightSurfaceContainerLowest,
    surfaceContainerLow = FallbackLightSurfaceContainerLow,
    surfaceContainer = FallbackLightSurfaceContainer,
    surfaceContainerHigh = FallbackLightSurfaceContainerHigh,
    surfaceContainerHighest = FallbackLightSurfaceContainerHighest,
    outline = FallbackLightOutline,
    outlineVariant = FallbackLightOutlineVariant
  )

private fun Color.deepenColor(factor: Float): Color {
  return Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha
  )
}

@Composable
fun MyApplicationTheme(
  themeMode: ThemeMode? = null,
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    null -> darkTheme
  }

  val context = LocalContext.current
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      if (isDark) {
        val base = dynamicDarkColorScheme(context)
        base.copy(
          background = base.background.deepenColor(0.48f),
          surface = base.surface.deepenColor(0.48f),
          surfaceContainerLowest = base.surfaceContainerLowest.deepenColor(0.38f),
          surfaceContainerLow = base.surfaceContainerLow.deepenColor(0.72f),
          surfaceContainer = base.surfaceContainer.deepenColor(0.95f),
          surfaceContainerHigh = base.surfaceContainerHigh.deepenColor(1.02f),
          surfaceContainerHighest = base.surfaceContainerHighest.deepenColor(1.08f)
        )
      } else {
        val base = dynamicLightColorScheme(context)
        base.copy(
          surfaceContainerLow = base.surfaceContainerLow.deepenColor(0.96f),
          surfaceContainer = base.surfaceContainer.deepenColor(0.93f),
          surfaceContainerHigh = base.surfaceContainerHigh.deepenColor(0.89f),
          surfaceContainerHighest = base.surfaceContainerHighest.deepenColor(0.86f)
        )
      }
    }
    isDark -> FallbackDarkColorScheme
    else -> FallbackLightColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}


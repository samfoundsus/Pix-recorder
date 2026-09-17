package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
  LIGHT,
  DARK,
  SYSTEM
}

val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }
val LocalIsDarkTheme = compositionLocalOf { false }

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

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
      }
    }
  }

  val context = LocalContext.current
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      if (isDark) {
        dynamicDarkColorScheme(context)
      } else {
        dynamicLightColorScheme(context)
      }
    }
    isDark -> FallbackDarkColorScheme
    else -> FallbackLightColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography) {
    CompositionLocalProvider(
      LocalThemeMode provides (themeMode ?: ThemeMode.SYSTEM),
      LocalIsDarkTheme provides isDark
    ) {
      key(isDark, themeMode) {
        content()
      }
    }
  }
}


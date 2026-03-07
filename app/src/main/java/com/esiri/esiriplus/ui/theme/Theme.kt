package com.esiri.esiriplus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.esiri.esiriplus.ui.preferences.FontScale
import com.esiri.esiriplus.ui.preferences.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal40.copy(alpha = 0.12f),
    onPrimaryContainer = Teal40,
    secondary = TealGrey40,
    onSecondary = Color.White,
    tertiary = Mint40,
    onTertiary = Color.White,
    background = SurfaceLight,
    onBackground = TextPrimary,
    surface = SurfaceContainerLight,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = OutlineLight,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Color(0xFF003730),
    primaryContainer = Teal40,
    onPrimaryContainer = Teal80,
    secondary = TealGrey80,
    onSecondary = Color(0xFF1B3B2F),
    tertiary = Mint80,
    onTertiary = Color(0xFF003730),
    background = SurfaceDark,
    onBackground = Color(0xFFE0E0E0),
    surface = SurfaceContainerDark,
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = OutlineDark,
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF690005),
)

/** Composition local for whether reduce-motion is enabled. */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun EsiriplusTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: FontScale = FontScale.NORMAL,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Apply font scale multiplier
    val density = LocalDensity.current
    val scaledDensity = when (fontScale) {
        FontScale.SMALL -> Density(density.density, fontScale = density.fontScale * 0.85f)
        FontScale.NORMAL -> density
        FontScale.LARGE -> Density(density.density, fontScale = density.fontScale * 1.2f)
    }

    // High contrast: bump onSurface to pure black/white for readability
    val finalColorScheme = if (highContrast) {
        if (darkTheme) {
            colorScheme.copy(
                onSurface = Color.White,
                onSurfaceVariant = Color(0xFFE0E0E0),
                onBackground = Color.White,
            )
        } else {
            colorScheme.copy(
                onSurface = Color.Black,
                onSurfaceVariant = Color(0xFF374151),
                onBackground = Color.Black,
            )
        }
    } else {
        colorScheme
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

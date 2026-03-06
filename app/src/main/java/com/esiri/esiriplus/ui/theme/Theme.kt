package com.esiri.esiriplus.ui.theme

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
    onBackground = Color.Black,
    surface = SurfaceContainerLight,
    onSurface = Color.Black,
    onSurfaceVariant = Color.Black,
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

@Composable
fun EsiriplusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

package com.esiri.esiriplus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    secondary = TealGrey40,
    tertiary = Mint40,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color.Black,
)

@Composable
fun EsiriplusTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
      colorScheme = LightColorScheme,
      typography = Typography,
      content = content
    )
}

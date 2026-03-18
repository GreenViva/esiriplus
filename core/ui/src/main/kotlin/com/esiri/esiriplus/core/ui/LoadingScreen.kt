package com.esiri.esiriplus.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BrandTeal = Color(0xFF2A9D8F)

/**
 * Full-screen centered loading spinner.
 */
@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    color: Color = BrandTeal,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = color)
    }
}

/**
 * Inline loading spinner for use inside buttons or small containers.
 */
@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = BrandTeal,
    strokeWidth: Dp = 2.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
    )
}

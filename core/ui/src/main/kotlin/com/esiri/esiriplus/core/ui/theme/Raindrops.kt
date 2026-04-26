package com.esiri.esiriplus.core.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Drives a single raindrop ripple's progress (0 → 1) on an
 * [InfiniteTransition]. Stagger drops with `delayMillis` so they don't
 * sync into one beat.
 */
@Composable
fun InfiniteTransition.dropPhase(
    durationMillis: Int,
    delayMillis: Int,
    label: String,
): State<Float> = animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        initialStartOffset = StartOffset(delayMillis, StartOffsetType.Delay),
        repeatMode = RepeatMode.Restart,
    ),
    label = label,
)

/**
 * Draws one raindrop ripple in the current [DrawScope]. The primary ring
 * expands from 2dp → 36dp and fades alpha 0.55 → 0; a secondary ring
 * lagging 18 % of the cycle behind fakes the dual-pulse you see when a
 * real drop hits water.
 *
 * `xRatio` / `yRatio` are 0–1 fractions of the canvas size. The default
 * ring color is white-translucent; pass a different `color` for a
 * non-white surface.
 */
fun DrawScope.drawRainRipple(
    xRatio: Float,
    yRatio: Float,
    phase: Float,
    color: Color = Color.White,
) {
    val center = Offset(size.width * xRatio, size.height * yRatio)
    val startRadius = 2.dp.toPx()
    val maxRadius = 36.dp.toPx()
    val radius = startRadius + (maxRadius - startRadius) * phase
    val alpha = (1f - phase).coerceIn(0f, 1f) * 0.55f
    val stroke = (1.6.dp.toPx()) * (1f - phase * 0.6f).coerceAtLeast(0.4f)
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(width = stroke),
    )
    val innerPhase = (phase - 0.18f).coerceIn(0f, 1f)
    val innerRadius = startRadius + (maxRadius - startRadius) * innerPhase
    val innerAlpha = (1f - innerPhase).coerceIn(0f, 1f) * 0.3f
    if (innerAlpha > 0f) {
        drawCircle(
            color = color.copy(alpha = innerAlpha),
            radius = innerRadius,
            center = center,
            style = Stroke(width = stroke * 0.7f),
        )
    }
}

/**
 * Convenience helper: 5 staggered raindrops at fixed positions across the
 * full size of the host. Drop into a parent that has a colored backdrop
 * (e.g. behind a hero card's content `Column`) by adding it as a sibling
 * before the content children inside a `Box`. Use the Modifier `modifier`
 * to constrain the drawing area (typically `Modifier.matchParentSize()`).
 */
@Composable
fun RainDropsCanvas(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    @Suppress("UNUSED_VARIABLE")
    val scope = rememberCoroutineScope()
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "raindrops")
    val drop1 by infinite.dropPhase(durationMillis = 2400, delayMillis = 0, label = "drop1")
    val drop2 by infinite.dropPhase(durationMillis = 2800, delayMillis = 600, label = "drop2")
    val drop3 by infinite.dropPhase(durationMillis = 2200, delayMillis = 1100, label = "drop3")
    val drop4 by infinite.dropPhase(durationMillis = 3000, delayMillis = 300, label = "drop4")
    val drop5 by infinite.dropPhase(durationMillis = 2600, delayMillis = 1500, label = "drop5")

    Canvas(modifier = modifier) {
        drawRainRipple(xRatio = 0.20f, yRatio = 0.30f, phase = drop1, color = color)
        drawRainRipple(xRatio = 0.75f, yRatio = 0.25f, phase = drop2, color = color)
        drawRainRipple(xRatio = 0.40f, yRatio = 0.65f, phase = drop3, color = color)
        drawRainRipple(xRatio = 0.85f, yRatio = 0.70f, phase = drop4, color = color)
        drawRainRipple(xRatio = 0.15f, yRatio = 0.85f, phase = drop5, color = color)
    }
}

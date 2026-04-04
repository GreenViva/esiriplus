package com.esiri.esiriplus.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BrandTeal = Color(0xFF2A9D8F)

/**
 * Pulsing down-arrow indicator that appears at the bottom of scrollable content
 * when there is more content to scroll. Disappears when user reaches the bottom.
 *
 * Use [ScrollIndicatorBox] to wrap your content, or call [PulsingScrollArrow]
 * directly and manage visibility yourself.
 */
@Composable
fun PulsingScrollArrow(
    modifier: Modifier = Modifier,
    tint: Color = BrandTeal,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scrollPulse")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrowBounce",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrowAlpha",
    )

    Surface(
        modifier = modifier
            .size(36.dp)
            .offset(y = offsetY.dp),
        shape = CircleShape,
        color = tint.copy(alpha = alpha * 0.15f),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll down for more",
                tint = tint.copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Wraps scrollable content and overlays a pulsing scroll arrow at the bottom
 * when there is more content below. Works with [ScrollState] (Column + verticalScroll).
 */
@Composable
fun ScrollIndicatorBox(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    tint: Color = BrandTeal,
    content: @Composable () -> Unit,
) {
    val canScrollDown by remember {
        derivedStateOf { scrollState.canScrollForward }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = canScrollDown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            PulsingScrollArrow(tint = tint)
        }
    }
}

/**
 * Wraps scrollable content and overlays a pulsing scroll arrow at the bottom
 * when there is more content below. Works with [LazyListState] (LazyColumn).
 */
@Composable
fun ScrollIndicatorBox(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    tint: Color = BrandTeal,
    content: @Composable () -> Unit,
) {
    val canScrollDown by remember {
        derivedStateOf { lazyListState.canScrollForward }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = canScrollDown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            PulsingScrollArrow(tint = tint)
        }
    }
}

package com.esiri.esiriplus.feature.auth.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.core.ui.theme.Geist
import com.esiri.esiriplus.core.ui.theme.InstrumentSerif
import com.esiri.esiriplus.feature.auth.R
import kotlinx.coroutines.delay

/* ──────────────────────────────────────────────────────────────────────────
 *  Brand palette — design spec
 * ────────────────────────────────────────────────────────────────────────── */
private object SplashColors {
    val Teal       = Color(0xFF2DBE9E)
    val TealDeep   = Color(0xFF1E8E76)
    val Cream      = Color(0xFFFBF9F3)
    val Mint       = Color(0xFFF0F7F2)
    val Ink        = Color(0xFF14201D)
    val InkSoft    = Color(0xFF2A3A36)
    val Muted      = Color(0xFF8A9893)
    val Hairline   = Color(0xFFDCE7E1)
    val Gold       = Color(0xFFC99A4A)
    val GoldSoft   = Color(0x1AC99A4A)
    val GoldBorder = Color(0x40C99A4A)
}

/* ──────────────────────────────────────────────────────────────────────────
 *  Typography
 *  Falls back to the system serif/sans until the design fonts (Instrument
 *  Serif, Geist) are added under res/font and wired up here.
 * ────────────────────────────────────────────────────────────────────────── */
private val EditorialSerif: FontFamily = InstrumentSerif
private val EditorialSans:  FontFamily = Geist

/* ──────────────────────────────────────────────────────────────────────────
 *  Entry point
 * ────────────────────────────────────────────────────────────────────────── */
@Composable
fun SplashScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ready by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        delay(3_000)
        ready = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(splashBackground())
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = { if (ready) onContinue() },
            ),
    ) {
        EditorialCornerMarks()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(8.dp))
            HeroBlock()
            BottomBlock(ready = ready)
        }
    }
}

@Composable
private fun splashBackground(): Brush = Brush.verticalGradient(
    colors = listOf(SplashColors.Cream, SplashColors.Mint),
)

/* ──────────────────────────────────────────────────────────────────────────
 *  Editorial corner brackets
 * ────────────────────────────────────────────────────────────────────────── */
@Composable
private fun EditorialCornerMarks() {
    val markSize = 28.dp
    val inset = 24.dp

    Box(Modifier.fillMaxSize().padding(inset)) {
        Box(Modifier.align(Alignment.TopStart).size(markSize).drawCorner(top = true, left = true))
        Box(Modifier.align(Alignment.TopEnd).size(markSize).drawCorner(top = true, left = false))
        Box(Modifier.align(Alignment.BottomStart).size(markSize).drawCorner(top = false, left = true))
        Box(Modifier.align(Alignment.BottomEnd).size(markSize).drawCorner(top = false, left = false))
    }
}

private fun Modifier.drawCorner(top: Boolean, left: Boolean): Modifier =
    this.then(
        Modifier.drawBehind {
            val s = 1.dp.toPx()
            val w = size.width
            val h = size.height
            val color = SplashColors.Hairline

            val xV = if (left) 0f else w - s
            drawRect(color, topLeft = Offset(xV, 0f), size = Size(s, h))
            val yH = if (top) 0f else h - s
            drawRect(color, topLeft = Offset(0f, yH), size = Size(w, s))
        },
    )

/* ──────────────────────────────────────────────────────────────────────────
 *  Hero block — emblem + wordmark + tagline
 * ────────────────────────────────────────────────────────────────────────── */
@Composable
private fun HeroBlock() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Emblem()
        Spacer(Modifier.height(36.dp))
        Wordmark()
        Spacer(Modifier.height(28.dp))
        Tagline()
    }
}

/* ──────────────────────────────────────────────────────────────────────────
 *  Emblem — rotating dotted ring + glowing seal + stethoscope + heartbeat
 * ────────────────────────────────────────────────────────────────────────── */
@Composable
private fun Emblem() {
    val infinite = rememberInfiniteTransition(label = "emblem")

    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )
    val glowScale by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_scale",
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )
    val pulseProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier.size(196.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Soft radial glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SplashColors.Teal.copy(alpha = 0.15f * glowAlpha),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius * glowScale,
                ),
                radius = radius * glowScale,
                center = center,
            )
        }

        // Rotating dotted ring
        Canvas(modifier = Modifier.size(172.dp)) {
            rotate(ringRotation) {
                val ringRadius = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(
                    color = SplashColors.Teal.copy(alpha = 0.25f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(4.dp.toPx(), 6.dp.toPx()),
                        ),
                    ),
                )
            }
        }

        // Seal
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.4f))
                .border(1.dp, SplashColors.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_stethoscope),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .padding(bottom = 12.dp),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .size(width = 90.dp, height = 14.dp),
            ) {
                HeartbeatLine(progress = pulseProgress)
            }
        }
    }
}

@Composable
private fun Stethoscope(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val color = SplashColors.TealDeep
        val stroke = 1.6.dp.toPx()
        val w = size.width
        val h = size.height

        fun x(v: Float) = v / 100f * w
        fun y(v: Float) = v / 100f * h

        drawCircle(color, radius = 2.5f / 100f * w, center = Offset(x(30f), y(16f)))
        drawCircle(color, radius = 2.5f / 100f * w, center = Offset(x(70f), y(16f)))

        val leftTube = Path().apply {
            moveTo(x(30f), y(18f))
            lineTo(x(30f), y(42f))
            quadraticBezierTo(x(30f), y(60f), x(50f), y(60f))
        }
        val rightTube = Path().apply {
            moveTo(x(70f), y(18f))
            lineTo(x(70f), y(42f))
            quadraticBezierTo(x(70f), y(60f), x(50f), y(60f))
        }
        val tubeStroke = Stroke(
            width = stroke,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawPath(leftTube, color, style = tubeStroke)
        drawPath(rightTube, color, style = tubeStroke)

        drawLine(
            color = color,
            start = Offset(x(50f), y(60f)),
            end = Offset(x(50f), y(76f)),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        drawCircle(
            color = color,
            radius = 6f / 100f * w,
            center = Offset(x(50f), y(82f)),
            style = Stroke(stroke),
        )
        drawCircle(
            color = color.copy(alpha = 0.5f),
            radius = 3f / 100f * w,
            center = Offset(x(50f), y(82f)),
            style = Stroke(1.dp.toPx()),
        )
    }
}

@Composable
private fun HeartbeatLine(progress: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val stroke = 1.4.dp.toPx()

        val full = Path().apply {
            moveTo(2f / 90f * w, midY)
            lineTo(28f / 90f * w, midY)
            lineTo(34f / 90f * w, 2f / 14f * h)
            lineTo(40f / 90f * w, 12f / 14f * h)
            lineTo(46f / 90f * w, 4f / 14f * h)
            lineTo(52f / 90f * w, midY)
            lineTo(88f / 90f * w, midY)
        }

        val measure = PathMeasure().apply { setPath(full, false) }
        val length = measure.length
        val drawn = length * progress

        drawPath(
            path = full,
            color = SplashColors.Teal,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(drawn, length),
                ),
            ),
        )
    }
}

/* ──────────────────────────────────────────────────────────────────────────
 *  Wordmark — "eSIRI Plus"
 * ────────────────────────────────────────────────────────────────────────── */
@Composable
private fun Wordmark() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = wordmarkText(),
            style = TextStyle(
                fontFamily = EditorialSerif,
                fontSize = 56.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-1.1).sp,
                color = SplashColors.Ink,
                lineHeight = 56.sp,
            ),
        )

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SplashColors.GoldSoft)
                .border(1.dp, SplashColors.GoldBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "PLUS",
                style = TextStyle(
                    fontFamily = EditorialSans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.2.sp,
                    color = SplashColors.Gold,
                ),
            )
        }
    }
}

@Composable
private fun wordmarkText() = buildAnnotatedString {
    withStyle(SpanStyle(color = SplashColors.Ink)) { append("e") }
    withStyle(
        SpanStyle(
            color = SplashColors.TealDeep,
            fontStyle = FontStyle.Italic,
        ),
    ) { append("SIRI") }
}

/* ──────────────────────────────────────────────────────────────────────────
 *  Tagline
 * ────────────────────────────────────────────────────────────────────────── */
@Composable
private fun Tagline() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "\"Afya yako, kipaumbele chetu.\"",
            style = TextStyle(
                fontFamily = EditorialSerif,
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                color = SplashColors.TealDeep,
                lineHeight = 26.sp,
                letterSpacing = (-0.1).sp,
                textAlign = TextAlign.Center,
            ),
        )

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .size(width = 32.dp, height = 1.dp)
                .background(SplashColors.Hairline),
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text = "YOUR HEALTH  ·  OUR PRIORITY",
            style = TextStyle(
                fontFamily = EditorialSans,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.4.sp,
                color = SplashColors.Muted,
            ),
        )
    }
}

/* ──────────────────────────────────────────────────────────────────────────
 *  Bottom block — progress + footer
 * ────────────────────────────────────────────────────────────────────────── */
@Composable
private fun BottomBlock(ready: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedProgressBar(ready = ready)

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (ready) "TAP TO CONTINUE" else "PREPARING YOUR SPACE",
            style = TextStyle(
                fontFamily = EditorialSans,
                fontSize = 11.sp,
                fontWeight = if (ready) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 1.8.sp,
                color = if (ready) SplashColors.TealDeep else SplashColors.Muted,
            ),
        )

        Spacer(Modifier.height(24.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SplashColors.Hairline),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Eden World Co.",
            style = TextStyle(
                fontFamily = EditorialSerif,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                color = SplashColors.InkSoft,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "DAR ES SALAAM  ·  TANZANIA  ·  EST. 2024",
            style = TextStyle(
                fontFamily = EditorialSans,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
                color = SplashColors.Muted,
            ),
        )
    }
}

@Composable
private fun AnimatedProgressBar(ready: Boolean) {
    var startFill by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { startFill = 1f }
    val fill by animateFloatAsState(
        targetValue = startFill,
        animationSpec = tween(durationMillis = 3_000, easing = FastOutSlowInEasing),
        label = "fill",
    )

    val infinite = rememberInfiniteTransition(label = "tap_pulse")
    val tapPulse by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tap_pulse",
    )

    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(SplashColors.Hairline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (ready) 1f else fill)
                .background(
                    if (ready) {
                        SplashColors.TealDeep.copy(alpha = tapPulse)
                    } else {
                        SplashColors.TealDeep
                    },
                ),
        )
    }
}

package com.esiri.esiriplus.feature.auth.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.ui.GradientBackground

private val BrandTeal = Color(0xFF2A9D8F)
private val DarkText = Color.Black
private val GrayText = Color.Black
private val LightGrayText = Color.Black

@Composable
fun SplashScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var tappable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(4000L)
        tappable = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 3000),
        label = "splash_fade_in",
    )

    GradientBackground(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = { if (tappable) onContinue() },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Teal circle with stethoscope icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(BrandTeal, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stethoscope),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App name
            Text(
                text = "eSIRI Plus",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Swahili tagline
            Text(
                text = "Afya yako, Kipaumbele chetu",
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                color = BrandTeal,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // English subtitle
            Text(
                text = "Your Health, Our Priority",
                fontSize = 14.sp,
                color = GrayText,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pagination dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(BrandTeal, CircleShape),
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Tap to continue
            Text(
                text = "Tap anywhere to continue",
                fontSize = 12.sp,
                color = LightGrayText,
            )
        }
    }
}

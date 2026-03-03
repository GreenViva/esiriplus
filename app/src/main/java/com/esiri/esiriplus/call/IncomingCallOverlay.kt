package com.esiri.esiriplus.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun IncomingCallOverlay(
    incomingCall: IncomingCall,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val callLabel = if (incomingCall.callType == "AUDIO") "Voice" else "Video"
    val callerLabel = if (incomingCall.callerRole == "doctor") "Your doctor" else "Your patient"

    // Auto-decline after 60 seconds (aligned with caller WAITING_TIMEOUT_MS)
    LaunchedEffect(incomingCall) {
        delay(60_000)
        onDecline()
    }

    // Pulsing animation for the call icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Pulsing call icon
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Incoming call",
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale),
                tint = Color.White,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Incoming $callLabel Call",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$callerLabel is calling",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Accept / Decline buttons
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFE53935), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Decline", color = Color.White, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.width(80.dp))

                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF43A047), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Accept", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

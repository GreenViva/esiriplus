package com.esiri.esiriplus.feature.chat.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.core.domain.model.ConsultationPhase

private val BrandTeal = Color(0xFF2A9D8F)
private val WarningOrange = Color(0xFFE76F51)
private val DangerRed = Color(0xFFE53E3E)

@Composable
fun ConsultationTimerBar(
    phase: ConsultationPhase,
    remainingSeconds: Int,
    extensionCount: Int,
    modifier: Modifier = Modifier,
) {
    val timerColor by animateColorAsState(
        targetValue = when {
            phase == ConsultationPhase.GRACE_PERIOD -> WarningOrange
            remainingSeconds <= 60 -> DangerRed
            remainingSeconds <= 180 -> WarningOrange
            else -> BrandTeal
        },
        label = "timerColor",
    )

    val phaseLabel = when (phase) {
        ConsultationPhase.ACTIVE -> ""
        ConsultationPhase.AWAITING_EXTENSION -> "Time ended"
        ConsultationPhase.GRACE_PERIOD -> "Grace period"
        ConsultationPhase.COMPLETED -> "Completed"
    }

    val timeDisplay = if (phase == ConsultationPhase.AWAITING_EXTENSION || phase == ConsultationPhase.COMPLETED) {
        "00:00"
    } else {
        formatTimer(remainingSeconds)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(timerColor.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\u23F1",
                fontSize = 18.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeDisplay,
                color = timerColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            if (phaseLabel.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = phaseLabel,
                    color = timerColor,
                    fontSize = 13.sp,
                )
            }
        }
        if (extensionCount > 0) {
            Text(
                text = "+$extensionCount ext",
                color = timerColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}

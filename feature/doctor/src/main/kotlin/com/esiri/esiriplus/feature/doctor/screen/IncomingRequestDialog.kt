package com.esiri.esiriplus.feature.doctor.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.feature.doctor.viewmodel.IncomingRequestUiState

private val BrandTeal = Color(0xFF2A9D8F)
private val AcceptGreen = Color(0xFF16A34A)
private val RejectRed = Color(0xFFDC2626)

@Composable
fun IncomingRequestDialog(
    state: IncomingRequestUiState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state.requestId == null) return

    Dialog(
        onDismissRequest = {
            // Only allow dismiss if already resolved
            if (state.responseStatus != null) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = state.responseStatus != null,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(BrandTeal.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (state.responseStatus) {
                            ConsultationRequestStatus.ACCEPTED -> "\u2713"
                            ConsultationRequestStatus.REJECTED -> "\u2717"
                            ConsultationRequestStatus.EXPIRED -> "\u23F0"
                            else -> "\uD83D\uDCDE" // phone icon
                        },
                        fontSize = 24.sp,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Title
                Text(
                    text = when (state.responseStatus) {
                        ConsultationRequestStatus.ACCEPTED -> "Request Accepted"
                        ConsultationRequestStatus.REJECTED -> "Request Declined"
                        ConsultationRequestStatus.EXPIRED -> "Request Expired"
                        else -> "Incoming Consultation Request"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = when (state.responseStatus) {
                        ConsultationRequestStatus.ACCEPTED -> "Redirecting to consultation..."
                        ConsultationRequestStatus.REJECTED -> "The patient will be notified."
                        ConsultationRequestStatus.EXPIRED -> "The request time has passed."
                        else -> "A patient is requesting a consultation. You have ${state.secondsRemaining} seconds to respond."
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )

                // Countdown bar (only when pending)
                if (state.responseStatus == null && state.secondsRemaining > 0) {
                    Spacer(Modifier.height(20.dp))

                    // Countdown timer badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (state.secondsRemaining <= 10) {
                            RejectRed.copy(alpha = 0.1f)
                        } else {
                            BrandTeal.copy(alpha = 0.1f)
                        },
                    ) {
                        Text(
                            text = "${state.secondsRemaining}s remaining",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.secondsRemaining <= 10) RejectRed else BrandTeal,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { state.secondsRemaining / 60f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (state.secondsRemaining <= 10) RejectRed else BrandTeal,
                        trackColor = Color(0xFFE5E7EB),
                    )
                }

                // Error message
                if (state.errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.errorMessage,
                        fontSize = 13.sp,
                        color = RejectRed,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Action buttons (only when pending and not yet responded)
                if (state.responseStatus == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Reject
                        OutlinedButton(
                            onClick = onReject,
                            enabled = !state.isResponding,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RejectRed),
                        ) {
                            Text(
                                text = "Decline",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.isResponding) Color.Gray else RejectRed,
                            )
                        }

                        // Accept
                        Button(
                            onClick = onAccept,
                            enabled = !state.isResponding,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AcceptGreen,
                                disabledContainerColor = AcceptGreen.copy(alpha = 0.5f),
                            ),
                        ) {
                            if (state.isResponding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = "Accept",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

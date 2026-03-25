package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.FollowUpRequestViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.FollowUpStatus

private val BrandTeal = Color(0xFF2A9D8F)
private val RoyalPurple = Color(0xFF4C1D95)
private val RoyalGold = Color(0xFFF59E0B)

@Composable
fun FollowUpWaitingScreen(
    onAccepted: (consultationId: String) -> Unit,
    onBack: () -> Unit,
    onBookAppointment: () -> Unit = {},
    onRequestSubstitute: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FollowUpRequestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate on acceptance
    LaunchedEffect(Unit) {
        viewModel.acceptedEvent.collect { event ->
            onAccepted(event.consultationId)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(RoyalPurple, Color(0xFF7C3AED)),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.tier_back),
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.followup_waiting_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (uiState.status) {
                    FollowUpStatus.SENDING -> {
                        SendingContent()
                    }
                    FollowUpStatus.WAITING -> {
                        WaitingContent(
                            secondsRemaining = uiState.secondsRemaining,
                            onCancel = onBack,
                        )
                    }
                    FollowUpStatus.ACCEPTED -> {
                        AcceptedContent()
                    }
                    FollowUpStatus.REJECTED -> {
                        DoctorUnavailableContent(
                            message = stringResource(R.string.followup_rejected),
                            icon = Icons.Filled.Close,
                            iconTint = Color(0xFFEF4444),
                            onBookAppointment = onBookAppointment,
                            onRequestSubstitute = onRequestSubstitute,
                            onGoBack = onBack,
                        )
                    }
                    FollowUpStatus.EXPIRED -> {
                        DoctorUnavailableContent(
                            message = stringResource(R.string.followup_expired),
                            icon = Icons.Filled.Schedule,
                            iconTint = Color(0xFFF59E0B),
                            onBookAppointment = onBookAppointment,
                            onRequestSubstitute = onRequestSubstitute,
                            onGoBack = onBack,
                        )
                    }
                    FollowUpStatus.ERROR -> {
                        TerminalContent(
                            message = uiState.errorMessage
                                ?: stringResource(R.string.followup_error),
                            icon = Icons.Filled.Close,
                            iconTint = Color(0xFFEF4444),
                            onGoBack = onBack,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            color = RoyalPurple,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.followup_requesting),
            color = Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WaitingContent(
    secondsRemaining: Int,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Countdown circle
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { secondsRemaining / 60f },
                modifier = Modifier.size(120.dp),
                color = RoyalPurple,
                trackColor = RoyalPurple.copy(alpha = 0.12f),
                strokeWidth = 6.dp,
            )
            Text(
                text = "${secondsRemaining}s",
                color = Color.Black,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.followup_waiting_response),
            color = Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.followup_seconds_remaining, secondsRemaining),
            color = Color.Black,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF3F4F6),
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.followup_cancel),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AcceptedContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(BrandTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.followup_accepted),
            color = Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TerminalContent(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onGoBack: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(iconTint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = message,
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGoBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandTeal,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.followup_go_back),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DoctorUnavailableContent(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onBookAppointment: () -> Unit,
    onRequestSubstitute: () -> Unit,
    onGoBack: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(iconTint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = message,
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.followup_unavailable_prompt),
            color = Color.Black,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        // Book Appointment — outlined / secondary
        OutlinedButton(
            onClick = onBookAppointment,
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandTeal),
        ) {
            Text(
                text = stringResource(R.string.followup_book_appointment),
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Request Another Doctor — filled / primary
        Button(
            onClick = onRequestSubstitute,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandTeal,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.followup_request_another_doctor),
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Go Back — small text button
        TextButton(onClick = onGoBack) {
            Text(
                text = stringResource(R.string.followup_go_back),
                color = Color.Black,
                fontSize = 13.sp,
            )
        }
    }
}

package com.esiri.esiriplus.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.viewmodel.DoctorLoginViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val CreamBackground = Color(0xFFF5F0EB)
private val SubtitleGray = Color.Black
private val FieldBorder = Color(0xFFE0E0E0)

@Composable
fun DoctorLoginScreen(
    onAuthenticated: () -> Unit,
    onBack: () -> Unit,
    onRegister: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DoctorLoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    // Device mismatch — show error screen
    if (uiState.deviceMismatch) {
        DeviceMismatchScreen(
            error = uiState.deviceMismatchError ?: "Device bound to another doctor",
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    // Banned doctor — show ban notice screen
    if (uiState.isBanned) {
        BannedDoctorScreen(
            banReason = uiState.banReason,
            bannedAt = uiState.bannedAt,
            onSignOut = {
                viewModel.clearBlockedState()
                onLogout()
            },
            modifier = modifier,
        )
        return
    }

    // Suspended doctor — show suspension notice screen
    if (uiState.isSuspended) {
        SuspendedDoctorScreen(
            suspendedUntil = uiState.suspendedUntil,
            suspensionReason = uiState.suspensionReason,
            onSignOut = {
                viewModel.clearBlockedState()
                onLogout()
            },
            modifier = modifier,
        )
        return
    }

    // Warning — doctor must acknowledge before proceeding
    if (uiState.hasWarning) {
        WarningDoctorScreen(
            warningMessage = uiState.warningMessage,
            isLoading = uiState.isLoading,
            onAcknowledge = { viewModel.acknowledgeWarning() },
            onSignOut = {
                viewModel.clearBlockedState()
                onLogout()
            },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Stethoscope icon in teal circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(BrandTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stethoscope),
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Doctor Portal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Sign in to manage your consultations",
            fontSize = 14.sp,
            color = SubtitleGray,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // White card container
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                // Tab row: Sign In | Register
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF2F2F2), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        onClick = { /* already on sign in */ },
                    ) {
                        Text(
                            text = "Sign In",
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                        )
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent,
                        onClick = onRegister,
                    ) {
                        Text(
                            text = "Register",
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = SubtitleGray,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Email",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("doctor@example.com", color = SubtitleGray, fontSize = 14.sp)
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_email),
                            contentDescription = null,
                            tint = SubtitleGray,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = FieldBorder,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Password",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022", color = SubtitleGray, fontSize = 14.sp)
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = null,
                            tint = SubtitleGray,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    if (passwordVisible) R.drawable.ic_visibility
                                    else R.drawable.ic_visibility_off,
                                ),
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = SubtitleGray,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = FieldBorder,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                )

                // Error message
                uiState.error?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sign In button
                Button(
                    onClick = { viewModel.login(onAuthenticated) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = uiState.isFormValid && !uiState.isLoading,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        disabledContainerColor = BrandTeal.copy(alpha = 0.5f),
                    ),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Sign In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceMismatchScreen(
    error: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
            .statusBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFFFEE2E2), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Device Not Authorized",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = error,
            fontSize = 14.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
        ) {
            Text(
                text = "Go Back",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun BannedDoctorScreen(
    banReason: String?,
    bannedAt: String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appealDeadline = remember(bannedAt) {
        try {
            val banned = java.time.Instant.parse(bannedAt)
            val deadline = banned.plus(java.time.Duration.ofDays(7))
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(deadline)
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Red circle with X icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFFEE2E2), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u2716",
                    fontSize = 36.sp,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Account Banned",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFDC2626),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "You have been banned from using this application.",
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )

            if (!banReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Reason",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFDC2626),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = banReason,
                        fontSize = 14.sp,
                        color = Color.Black,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Appeal info box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F9FF))
                    .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = "Have a complaint?",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0369A1),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (appealDeadline != null) {
                        "You may contact our support team to appeal this decision within 7 days (by $appealDeadline)."
                    } else {
                        "You may contact our support team to appeal this decision within 7 days of the ban."
                    },
                    fontSize = 13.sp,
                    color = Color.Black,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "support@esiriplus.com",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0369A1),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = onSignOut) {
                Text(
                    text = "Sign Out",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280),
                )
            }
        }
    }
}

@Composable
private fun SuspendedDoctorScreen(
    suspendedUntil: String?,
    suspensionReason: String?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val suspensionEnd = remember(suspendedUntil) {
        try {
            val until = java.time.Instant.parse(suspendedUntil)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(until)
        } catch (_: Exception) {
            null
        }
    }

    val remainingTime = remember(suspendedUntil) {
        try {
            val until = java.time.Instant.parse(suspendedUntil)
            val now = java.time.Instant.now()
            val duration = java.time.Duration.between(now, until)
            val days = duration.toDays()
            val hours = duration.toHours() % 24
            when {
                days > 0 -> "$days day${if (days > 1) "s" else ""} and $hours hour${if (hours != 1L) "s" else ""}"
                hours > 0 -> "$hours hour${if (hours != 1L) "s" else ""}"
                else -> "less than an hour"
            }
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Orange circle with pause icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFFFF7ED), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u23F8",
                    fontSize = 36.sp,
                    color = Color(0xFFEA580C),
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Account Suspended",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEA580C),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your account has been temporarily suspended.",
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )

            if (!suspensionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF7ED))
                        .border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Reason",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEA580C),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = suspensionReason,
                        fontSize = 14.sp,
                        color = Color.Black,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Time remaining box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F9FF))
                    .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = "Suspension Details",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0369A1),
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (remainingTime != null) {
                    Text(
                        text = "Time remaining: $remainingTime",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (suspensionEnd != null) {
                    Text(
                        text = "Your suspension will be automatically lifted on $suspensionEnd.",
                        fontSize = 13.sp,
                        color = Color.Black,
                    )
                } else {
                    Text(
                        text = "Your suspension will be automatically lifted when the period ends.",
                        fontSize = 13.sp,
                        color = Color.Black,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "support@esiriplus.com",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0369A1),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = onSignOut) {
                Text(
                    text = "Sign Out",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280),
                )
            }
        }
    }
}

@Composable
private fun WarningDoctorScreen(
    warningMessage: String?,
    isLoading: Boolean,
    onAcknowledge: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Amber circle with warning icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFFEF3C7), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u26A0",
                    fontSize = 36.sp,
                    color = Color(0xFFD97706),
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Warning Notice",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD97706),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "You have received a warning from the administration.",
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )

            if (!warningMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFFBEB))
                        .border(1.dp, Color(0xFFFCD34D), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Warning Details",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD97706),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = warningMessage,
                        fontSize = 14.sp,
                        color = Color.Black,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Info box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F9FF))
                    .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = "Please take note",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0369A1),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Please review this warning carefully. Repeated violations may result in suspension or a permanent ban of your account.",
                    fontSize = 13.sp,
                    color = Color.Black,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "support@esiriplus.com",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0369A1),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Acknowledge button
            Button(
                onClick = onAcknowledge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD97706),
                    disabledContainerColor = Color(0xFFD97706).copy(alpha = 0.5f),
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "I Understand",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onSignOut) {
                Text(
                    text = "Sign Out",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280),
                )
            }
        }
    }
}

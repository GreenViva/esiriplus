package com.esiri.esiriplus.feature.patient.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.ui.LanguageSwitchButton
import com.esiri.esiriplus.core.ui.ScrollIndicatorBox
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.PatientHomeViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    onStartConsultation: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToConsultationHistory: () -> Unit,
    onNavigateToAppointments: () -> Unit,
    onNavigateToOngoingConsultations: () -> Unit,
    onResumeConsultation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Request POST_NOTIFICATIONS permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not — FCM will work either way, just no system tray */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
            },
            onDismiss = { showLogoutDialog = false },
        )
    }

    // Pending rating bottom sheet
    val pendingRating = uiState.pendingRatingConsultation
    var showPendingRating by remember(pendingRating?.consultationId) {
        mutableStateOf(pendingRating != null)
    }
    if (showPendingRating && pendingRating != null) {
        RatingBottomSheet(
            consultationId = pendingRating.consultationId,
            doctorId = pendingRating.doctorId,
            patientSessionId = pendingRating.patientSessionId,
            onDismiss = { showPendingRating = false; viewModel.dismissPendingRating() },
            onSubmitSuccess = { showPendingRating = false; viewModel.clearPendingRating() },
        )
    }

    val pullRefreshState = rememberPullToRefreshState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.background, MintLight))
            ),
    ) {
        val scrollState = rememberScrollState()
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
        ) {
        ScrollIndicatorBox(scrollState = scrollState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            // Welcome Header
            WelcomeHeader(
                maskedPatientId = uiState.maskedPatientId,
                patientId = uiState.patientId,
                context = context,
            )

            Spacer(Modifier.height(16.dp))

            // Settings Row
            SettingsRow(
                soundsEnabled = uiState.soundsEnabled,
                onToggleSounds = viewModel::toggleSounds,
                onLogout = { showLogoutDialog = true },
            )

            Spacer(Modifier.height(16.dp))

            // Your Medical Info
            MedicalInfoSection(onEdit = onNavigateToProfile)

            Spacer(Modifier.height(2.dp))

            // Quick Action Chips
            QuickActionChips(
                onServicesClick = { onStartConsultation("") },
                onNewConsultationClick = { onStartConsultation("") },
                onReportsClick = onNavigateToReports,
            )

            Spacer(Modifier.height(12.dp))

            // Start Consultation Card
            StartConsultationCard(onClick = { onStartConsultation("") })

            Spacer(Modifier.height(12.dp))

            // Ongoing Consultations
            OngoingConsultationsCard(
                count = uiState.ongoingConsultations.size,
                onClick = onNavigateToOngoingConsultations,
            )

            Spacer(Modifier.height(12.dp))

            // Consultation History
            DashboardSectionCard(
                iconRes = R.drawable.ic_consultation,
                title = stringResource(R.string.home_consultation_history),
                subtitle = stringResource(R.string.home_consultation_history_subtitle),
                onClick = onNavigateToConsultationHistory,
            )

            Spacer(Modifier.height(12.dp))

            // Reports
            DashboardSectionCard(
                iconRes = R.drawable.ic_reports,
                title = stringResource(R.string.home_reports),
                subtitle = stringResource(R.string.home_reports_subtitle),
                onClick = onNavigateToReports,
                showBadge = uiState.hasUnreadReports,
            )

            Spacer(Modifier.height(12.dp))

            // My Appointments
            DashboardSectionCard(
                iconRes = R.drawable.ic_calendar,
                title = stringResource(R.string.home_my_appointments),
                subtitle = stringResource(R.string.home_my_appointments_subtitle),
                onClick = onNavigateToAppointments,
            )

            Spacer(Modifier.height(16.dp))

            // Contact Us
            ContactUsSection()

            Spacer(Modifier.height(8.dp))
        }
        } // ScrollIndicatorBox
        } // PullToRefreshBox

        // Pulsing chat FAB when there is an active consultation
        val activeConsultation = uiState.activeConsultation
        if (activeConsultation != null) {
            ActiveChatFab(
                onClick = { onResumeConsultation(activeConsultation.consultationId) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            )
        }
    }
}

@Composable
private fun WelcomeHeader(
    maskedPatientId: String,
    patientId: String,
    context: Context,
) {
    Text(
        text = stringResource(R.string.home_welcome_back),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.home_your_patient_id),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(
                text = maskedPatientId,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.5.sp,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Patient ID", patientId))
                Toast.makeText(context, context.getString(R.string.home_patient_id_copied), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = stringResource(R.string.home_copy_patient_id),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    soundsEnabled: Boolean,
    onToggleSounds: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        // Language
        LanguageSwitchButton(
            showLabel = true,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.width(16.dp))

        // Sounds
        Icon(
            painter = painterResource(R.drawable.ic_volume),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.home_sounds),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        Switch(
            checked = soundsEnabled,
            onCheckedChange = { onToggleSounds() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandTeal,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )

        Spacer(Modifier.width(12.dp))

        // Log Out
        TextButton(onClick = onLogout) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.home_log_out),
                color = Color.Red,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun MedicalInfoSection(onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_heart),
            contentDescription = null,
            tint = BrandTeal,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_your_medical_info),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(
            onClick = onEdit,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.home_edit),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun QuickActionChips(
    onServicesClick: () -> Unit,
    onNewConsultationClick: () -> Unit,
    onReportsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionChip(
            iconRes = R.drawable.ic_services,
            label = stringResource(R.string.home_chip_services),
            onClick = onServicesClick,
        )
        ActionChip(
            iconRes = R.drawable.ic_consultation,
            label = stringResource(R.string.home_chip_new_consultation),
            onClick = onNewConsultationClick,
        )
        ActionChip(
            iconRes = R.drawable.ic_reports,
            label = stringResource(R.string.home_chip_reports),
            onClick = onReportsClick,
        )
    }
}

@Composable
private fun ActionChip(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StartConsultationCard(onClick: () -> Unit) {
    val tealLight = Color(0xFF3DB8A9)

    // Waves radiating around the whole badge every 1.5s
    val infiniteTransition = rememberInfiniteTransition(label = "consultPulse")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave1",
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave2",
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave3",
    )
    // Stethoscope breathing pulse
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "iconPulse",
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Wave 1 - rounded rect ripple around the whole badge
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val s = 1f + wave1 * 0.06f
                    scaleX = s
                    scaleY = s
                    alpha = (1f - wave1) * 0.5f
                }
                .border(2.dp, BrandTeal, RoundedCornerShape(16.dp)),
        )
        // Wave 2
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val s = 1f + wave2 * 0.06f
                    scaleX = s
                    scaleY = s
                    alpha = (1f - wave2) * 0.35f
                }
                .border(1.5.dp, BrandTeal, RoundedCornerShape(16.dp)),
        )
        // Wave 3
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val s = 1f + wave3 * 0.06f
                    scaleX = s
                    scaleY = s
                    alpha = (1f - wave3) * 0.2f
                }
                .border(1.dp, BrandTeal, RoundedCornerShape(16.dp)),
        )

        // The card
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BrandTeal),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                BrandTeal,
                                tealLight.copy(alpha = 0.85f),
                                BrandTeal,
                            ),
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Stethoscope with circular waves from it
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Circle wave 1
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                val s = 0.5f + wave1 * 0.5f
                                scaleX = s
                                scaleY = s
                                alpha = (1f - wave1) * 0.6f
                            }
                            .border(2.dp, Color.White, CircleShape),
                    )
                    // Circle wave 2
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                val s = 0.5f + wave2 * 0.5f
                                scaleX = s
                                scaleY = s
                                alpha = (1f - wave2) * 0.45f
                            }
                            .border(1.5.dp, Color.White, CircleShape),
                    )
                    // Circle wave 3
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                val s = 0.5f + wave3 * 0.5f
                                scaleX = s
                                scaleY = s
                                alpha = (1f - wave3) * 0.3f
                            }
                            .border(1.dp, Color.White, CircleShape),
                    )
                    // Stethoscope icon (source of waves)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_stethoscope),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_start_consultation),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_start_consultation_subtitle),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.home_content_desc_start),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OngoingConsultationsCard(count: Int, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.4f)),
        colors = CardDefaults.outlinedCardColors(containerColor = BrandTeal.copy(alpha = 0.06f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = BrandTeal.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_consultation),
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_ongoing_consultations),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_ongoing_consultations_subtitle),
                    fontSize = 13.sp,
                    color = Color.Black,
                )
            }
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(BrandTeal, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = count.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.home_content_desc_open),
                tint = BrandTeal,
            )
        }
    }
}

@Composable
private fun DashboardSectionCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showBadge: Boolean = false,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                if (showBadge) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.Red, CircleShape)
                            .align(Alignment.TopEnd),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.home_content_desc_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.home_logout_title),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.home_logout_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.home_logout_confirm),
                    color = Color.Red,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.home_logout_cancel),
                    color = BrandTeal,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun ActiveChatFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fabScale",
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = scale * 1.2f
                    scaleY = scale * 1.2f
                }
                .background(
                    color = BrandTeal.copy(alpha = glowAlpha * 0.3f),
                    shape = CircleShape,
                ),
        )

        // Main FAB button
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = CircleShape,
            containerColor = BrandTeal,
            contentColor = Color.White,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.home_content_desc_active_consultation),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun ContactUsSection() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "For help contact us",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "+255 663 582 994",
                fontSize = 12.sp,
                color = BrandTeal,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+255663582994")))
                },
            )
            Spacer(Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "support@esiri.africa",
                fontSize = 12.sp,
                color = BrandTeal,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@esiri.africa")))
                },
            )
        }
    }
}


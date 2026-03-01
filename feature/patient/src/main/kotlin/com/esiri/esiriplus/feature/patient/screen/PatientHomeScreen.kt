package com.esiri.esiriplus.feature.patient.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.PatientHomeViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val IconBg = Color(0xFFF0FDFA)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF1F2937)

@Composable
fun PatientHomeScreen(
    onStartConsultation: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToConsultationHistory: () -> Unit,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(Color.White, MintLight))
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                languageDisplayName = uiState.languageDisplayName,
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

            // Consultation History
            DashboardSectionCard(
                iconRes = R.drawable.ic_consultation,
                title = "Consultation History",
                subtitle = "View your past consultations",
                onClick = onNavigateToConsultationHistory,
            )

            Spacer(Modifier.height(12.dp))

            // Reports
            DashboardSectionCard(
                iconRes = R.drawable.ic_reports,
                title = "Reports",
                subtitle = "View and download your reports",
                onClick = onNavigateToReports,
            )

            Spacer(Modifier.height(12.dp))

            // My Appointments
            MyAppointmentsSection()
        }

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
        text = "Welcome Back \uD83D\uDC4B",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Your Patient ID:",
            color = SubtitleGrey,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF3F4F6),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Text(
                text = maskedPatientId,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                letterSpacing = 0.5.sp,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Patient ID", patientId))
                Toast.makeText(context, "Patient ID copied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = "Copy Patient ID",
                tint = SubtitleGrey,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    languageDisplayName: String,
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
        Icon(
            painter = painterResource(R.drawable.ic_language),
            contentDescription = null,
            tint = SubtitleGrey,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = languageDisplayName,
            fontSize = 14.sp,
            color = Color.Black,
        )

        Spacer(Modifier.width(16.dp))

        // Sounds
        Icon(
            painter = painterResource(R.drawable.ic_volume),
            contentDescription = null,
            tint = SubtitleGrey,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Sounds",
            fontSize = 14.sp,
            color = Color.Black,
        )
        Spacer(Modifier.width(4.dp))
        Switch(
            checked = soundsEnabled,
            onCheckedChange = { onToggleSounds() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandTeal,
                uncheckedTrackColor = Color(0xFFE5E7EB),
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
                text = "Log Out",
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
            text = "Your Medical Info",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onEdit,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Edit",
                color = Color.Black,
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
            label = "Services",
            onClick = onServicesClick,
        )
        ActionChip(
            iconRes = R.drawable.ic_consultation,
            label = "New Consultation",
            onClick = onNewConsultationClick,
        )
        ActionChip(
            iconRes = R.drawable.ic_reports,
            label = "Reports",
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
        border = BorderStroke(1.dp, CardBorder),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
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
                color = Color.Black,
            )
        }
    }
}

@Composable
private fun StartConsultationCard(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Stethoscope icon in circle bg
            Surface(
                shape = CircleShape,
                color = IconBg,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stethoscope),
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Start Consultation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose a service and connect with verified doctors",
                    fontSize = 14.sp,
                    color = SubtitleGrey,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Start",
                tint = SubtitleGrey,
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
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = IconBg,
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
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = SubtitleGrey,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = SubtitleGrey,
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
                text = "Log Out",
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        },
        text = {
            Text(
                text = "Are you sure you want to log out? You will need your Patient ID to log back in.",
                color = SubtitleGrey,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Log Out",
                    color = Color.Red,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = BrandTeal,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        containerColor = Color.White,
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
                contentDescription = "Return to active consultation",
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun MyAppointmentsSection() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_calendar),
            contentDescription = null,
            tint = BrandTeal,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "My Appointments",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.Black,
        )
    }
    Spacer(Modifier.height(12.dp))
    // Empty state
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = null,
                    tint = CardBorder,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No appointments yet",
                    color = SubtitleGrey,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

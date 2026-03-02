package com.esiri.esiriplus.feature.admin.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.esiri.esiriplus.core.domain.model.DoctorStatus
import com.esiri.esiriplus.feature.admin.viewmodel.AdminDoctorRow
import com.esiri.esiriplus.feature.admin.viewmodel.AdminDoctorViewModel

private val BrandTeal = Color(0xFF2A9D8F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDetailScreen(
    doctorId: String,
    viewModel: AdminDoctorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog state
    var showRejectDialog by remember { mutableStateOf(false) }
    var showBanDialog by remember { mutableStateOf(false) }
    var showSuspendDialog by remember { mutableStateOf(false) }
    var showWarnDialog by remember { mutableStateOf(false) }
    var dialogInput by remember { mutableStateOf("") }
    var suspendDays by remember { mutableFloatStateOf(7f) }

    LaunchedEffect(doctorId) {
        viewModel.selectDoctor(doctorId)
    }

    LaunchedEffect(uiState.actionResult) {
        uiState.actionResult?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.clearActionResult()
        }
    }

    val doctor = uiState.doctors.find { it.doctorId == doctorId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doctor Details", color = Color.Black, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        if (doctor == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = BrandTeal)
                } else {
                    Text("Doctor not found", color = Color.Black)
                }
            }
            return@Scaffold
        }

        val status = doctor.computeStatus()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile header
            ProfileHeader(doctor, status)

            // Contact info
            ContactInfoCard(doctor)

            // Professional info
            ProfessionalInfoCard(doctor)

            // Status detail
            StatusDetailCard(doctor, status)

            // Action buttons
            ActionButtons(
                doctor = doctor,
                status = status,
                actionInProgress = uiState.actionInProgress,
                onApprove = { viewModel.approveDoctor(doctorId) },
                onReject = { showRejectDialog = true },
                onBan = { showBanDialog = true },
                onSuspend = { showSuspendDialog = true },
                onWarn = { showWarnDialog = true },
                onUnsuspend = { viewModel.unsuspendDoctor(doctorId) },
                onUnban = { viewModel.unbanDoctor(doctorId) },
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showRejectDialog) {
        InputDialog(
            title = "Reject Doctor",
            label = "Rejection reason",
            value = dialogInput,
            onValueChange = { dialogInput = it },
            onConfirm = {
                viewModel.rejectDoctor(doctorId, dialogInput)
                dialogInput = ""
                showRejectDialog = false
            },
            onDismiss = {
                dialogInput = ""
                showRejectDialog = false
            },
            confirmText = "Reject",
            confirmColor = Color(0xFFDC2626),
        )
    }

    if (showBanDialog) {
        InputDialog(
            title = "Ban Doctor",
            label = "Ban reason",
            value = dialogInput,
            onValueChange = { dialogInput = it },
            onConfirm = {
                viewModel.banDoctor(doctorId, dialogInput)
                dialogInput = ""
                showBanDialog = false
            },
            onDismiss = {
                dialogInput = ""
                showBanDialog = false
            },
            confirmText = "Ban",
            confirmColor = Color(0xFF7C2D12),
        )
    }

    if (showSuspendDialog) {
        SuspendDialog(
            reason = dialogInput,
            onReasonChange = { dialogInput = it },
            days = suspendDays,
            onDaysChange = { suspendDays = it },
            onConfirm = {
                viewModel.suspendDoctor(doctorId, suspendDays.toInt(), dialogInput)
                dialogInput = ""
                suspendDays = 7f
                showSuspendDialog = false
            },
            onDismiss = {
                dialogInput = ""
                suspendDays = 7f
                showSuspendDialog = false
            },
        )
    }

    if (showWarnDialog) {
        InputDialog(
            title = "Warn Doctor",
            label = "Warning message",
            value = dialogInput,
            onValueChange = { dialogInput = it },
            onConfirm = {
                viewModel.warnDoctor(doctorId, dialogInput)
                dialogInput = ""
                showWarnDialog = false
            },
            onDismiss = {
                dialogInput = ""
                showWarnDialog = false
            },
            confirmText = "Send Warning",
            confirmColor = Color(0xFFD97706),
        )
    }
}

// ── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(doctor: AdminDoctorRow, status: DoctorStatus) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (doctor.profilePhotoUrl != null) {
                AsyncImage(
                    model = doctor.profilePhotoUrl,
                    contentDescription = doctor.fullName,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(BrandTeal.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                doctor.fullName,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )

            Text(
                doctor.specialty.replaceFirstChar { it.uppercase() }.replace("_", " "),
                color = Color.Black,
                fontSize = 14.sp,
            )

            if (!doctor.specialistField.isNullOrBlank()) {
                Text(doctor.specialistField, color = Color.Black, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            StatusBadge(status)

            if (doctor.averageRating > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "%.1f".format(doctor.averageRating),
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        " (${doctor.totalRatings} reviews)",
                        color = Color.Black,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactInfoCard(doctor: AdminDoctorRow) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Contact Information", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))

            InfoRow(Icons.Default.Email, "Email", doctor.email)
            Spacer(Modifier.height(8.dp))
            InfoRow(Icons.Default.Phone, "Phone", "${doctor.countryCode} ${doctor.phone}")

            if (doctor.country.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Country: ${doctor.country}", color = Color.Black, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = Color.Black, fontSize = 12.sp)
            Text(value, color = Color.Black, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ProfessionalInfoCard(doctor: AdminDoctorRow) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Professional Information", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))

            DetailRow("License Number", doctor.licenseNumber)
            DetailRow("Experience", "${doctor.yearsExperience} years")

            if (doctor.languages.isNotEmpty()) {
                DetailRow("Languages", doctor.languages.joinToString(", "))
            }
            if (doctor.services.isNotEmpty()) {
                DetailRow("Services", doctor.services.joinToString(", "))
            }
            if (doctor.bio.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Bio", color = Color.Black, fontSize = 12.sp)
                Text(doctor.bio, color = Color.Black, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.Black, fontSize = 13.sp)
        Text(value, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusDetailCard(doctor: AdminDoctorRow, status: DoctorStatus) {
    val hasDetail = when (status) {
        DoctorStatus.REJECTED -> !doctor.rejectionReason.isNullOrBlank()
        DoctorStatus.SUSPENDED -> !doctor.suspensionReason.isNullOrBlank()
        DoctorStatus.BANNED -> !doctor.banReason.isNullOrBlank()
        else -> !doctor.warningMessage.isNullOrBlank()
    }

    if (!hasDetail) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                DoctorStatus.REJECTED -> Color(0xFFFEE2E2)
                DoctorStatus.SUSPENDED -> Color(0xFFFED7AA)
                DoctorStatus.BANNED -> Color(0xFFFEE2E2)
                else -> Color(0xFFFFF3CD)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                when (status) {
                    DoctorStatus.REJECTED -> "Rejection Details"
                    DoctorStatus.SUSPENDED -> "Suspension Details"
                    DoctorStatus.BANNED -> "Ban Details"
                    else -> "Warning"
                },
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))

            when (status) {
                DoctorStatus.REJECTED -> {
                    Text("Reason: ${doctor.rejectionReason}", color = Color.Black, fontSize = 14.sp)
                }
                DoctorStatus.SUSPENDED -> {
                    Text("Reason: ${doctor.suspensionReason}", color = Color.Black, fontSize = 14.sp)
                    if (doctor.suspendedUntil != null) {
                        Text("Until: ${doctor.suspendedUntil}", color = Color.Black, fontSize = 13.sp)
                    }
                }
                DoctorStatus.BANNED -> {
                    Text("Reason: ${doctor.banReason}", color = Color.Black, fontSize = 14.sp)
                    if (doctor.bannedAt != null) {
                        Text("Banned at: ${doctor.bannedAt}", color = Color.Black, fontSize = 13.sp)
                    }
                }
                else -> {
                    Text(doctor.warningMessage ?: "", color = Color.Black, fontSize = 14.sp)
                    if (doctor.warningAt != null) {
                        Text("Warned at: ${doctor.warningAt}", color = Color.Black, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    doctor: AdminDoctorRow,
    status: DoctorStatus,
    actionInProgress: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onBan: () -> Unit,
    onSuspend: () -> Unit,
    onWarn: () -> Unit,
    onUnsuspend: () -> Unit,
    onUnban: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Actions", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)

            if (actionInProgress) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandTeal, modifier = Modifier.size(24.dp))
                }
                return@Column
            }

            when (status) {
                DoctorStatus.PENDING -> {
                    ActionButton("Approve", BrandTeal, onApprove)
                    ActionButton("Reject", Color(0xFFDC2626), onReject)
                }
                DoctorStatus.ACTIVE -> {
                    ActionButton("Warn", Color(0xFFD97706), onWarn)
                    ActionButton("Suspend", Color(0xFFEA580C), onSuspend)
                    ActionButton("Ban", Color(0xFF7C2D12), onBan)
                }
                DoctorStatus.SUSPENDED -> {
                    ActionButton("Unsuspend", BrandTeal, onUnsuspend)
                    ActionButton("Ban", Color(0xFF7C2D12), onBan)
                }
                DoctorStatus.REJECTED -> {
                    ActionButton("Approve", BrandTeal, onApprove)
                }
                DoctorStatus.BANNED -> {
                    ActionButton("Unban", BrandTeal, onUnban)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun InputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String,
    confirmColor: Color,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.Black) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, color = Color.Black) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandTeal,
                    cursorColor = BrandTeal,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                ),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor),
            ) {
                Text(confirmText, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Black)
            }
        },
    )
}

@Composable
private fun SuspendDialog(
    reason: String,
    onReasonChange: (String) -> Unit,
    days: Float,
    onDaysChange: (Float) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suspend Doctor", color = Color.Black) },
        text = {
            Column {
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Suspension reason", color = Color.Black) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        cursorColor = BrandTeal,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Duration: ${days.toInt()} days",
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )

                Slider(
                    value = days,
                    onValueChange = onDaysChange,
                    valueRange = 1f..365f,
                    steps = 0,
                    colors = SliderDefaults.colors(
                        thumbColor = BrandTeal,
                        activeTrackColor = BrandTeal,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
            ) {
                Text("Suspend", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Black)
            }
        },
    )
}

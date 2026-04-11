package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.Appointment
import com.esiri.esiriplus.core.domain.model.AppointmentStatus
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.AppointmentTab
import com.esiri.esiriplus.feature.patient.viewmodel.PatientAppointmentsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF374151)
private val SuccessGreen = Color(0xFF16A34A)
private val WarningOrange = Color(0xFFEA580C)
private val ErrorRed = Color(0xFFDC2626)

private val EAT = ZoneId.of("Africa/Nairobi")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientAppointmentsScreen(
    onBack: () -> Unit,
    onConsultationAccepted: (consultationId: String) -> Unit = {},
    onFindAnotherDoctor: (serviceType: String, appointmentId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: PatientAppointmentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    var cancelConfirmAppointmentId by remember { mutableStateOf<String?>(null) }

    // Navigate to chat when doctor accepts
    LaunchedEffect(Unit) {
        viewModel.acceptedEvent.collect { event ->
            onConsultationAccepted(event.consultationId)
        }
    }

    // Navigate to find another doctor
    LaunchedEffect(Unit) {
        viewModel.findAnotherEvent.collect { appt ->
            onFindAnotherDoctor(appt.serviceType, appt.appointmentId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color.White, MintLight))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onBack),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.appointments_back),
                        tint = BrandTeal,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = stringResource(R.string.appointments_back), fontSize = 14.sp, color = BrandTeal)
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.appointments_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            // Tab bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppointmentTab.entries.forEach { tab ->
                    val isSelected = uiState.selectedTab == tab
                    val label = when (tab) {
                        AppointmentTab.UPCOMING -> stringResource(R.string.appointments_tab_upcoming)
                        AppointmentTab.PAST -> stringResource(R.string.appointments_tab_past)
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) BrandTeal else Color.White,
                        border = BorderStroke(1.dp, if (isSelected) BrandTeal else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.selectTab(tab) },
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier
                                .padding(vertical = 10.dp)
                                .fillMaxWidth(),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color.Black,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder, thickness = 1.dp)

            // Content
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullRefreshState,
                modifier = Modifier.weight(1f),
            ) {
                if (uiState.isLoading) {
                    com.esiri.esiriplus.core.ui.LoadingScreen()
                } else {
                    val appointments = when (uiState.selectedTab) {
                        AppointmentTab.UPCOMING -> uiState.upcomingAppointments
                        AppointmentTab.PAST -> uiState.pastAppointments
                    }

                    if (appointments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_calendar),
                                    contentDescription = null,
                                    tint = BrandTeal.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp),
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = when (uiState.selectedTab) {
                                        AppointmentTab.UPCOMING -> stringResource(R.string.appointments_no_upcoming)
                                        AppointmentTab.PAST -> stringResource(R.string.appointments_no_past)
                                    },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(appointments, key = { it.appointmentId }) { appointment ->
                                val isWaiting = uiState.activeRequestAppointmentId == appointment.appointmentId
                                AppointmentCard(
                                    appointment = appointment,
                                    isCancelling = uiState.isCancelling == appointment.appointmentId,
                                    isStarting = uiState.isStarting == appointment.appointmentId,
                                    isWaitingForDoctor = isWaiting,
                                    secondsRemaining = if (isWaiting) uiState.secondsRemaining else 0,
                                    onCancel = { cancelConfirmAppointmentId = appointment.appointmentId },
                                    onStartConsultation = { viewModel.startConsultation(appointment) },
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }

            // Error banner
            if (uiState.errorMessage != null) {
                com.esiri.esiriplus.core.ui.ErrorBanner(
                    message = uiState.errorMessage ?: "",
                    onDismiss = { viewModel.dismissError() },
                )
            }
        }

        // Doctor unavailable dialog
        if (uiState.showDoctorUnavailableDialog && uiState.unavailableAppointment != null) {
            val appt = uiState.unavailableAppointment!!
            AlertDialog(
                onDismissRequest = { viewModel.dismissDoctorUnavailableDialog() },
                title = { Text("Doctor Unavailable", color = Color.Black, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "The doctor is currently unavailable or in another session. Would you like to find another doctor for this appointment?",
                        color = Color.Black,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.findAnotherDoctor() },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    ) {
                        Text("Find Another Doctor", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.dismissDoctorUnavailableDialog() }) {
                        Text("Cancel", color = Color.Black)
                    }
                },
            )
        }

        // Cancel confirmation dialog
        if (cancelConfirmAppointmentId != null) {
            AlertDialog(
                onDismissRequest = { cancelConfirmAppointmentId = null },
                title = { Text("Cancel Appointment", color = Color.Black, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to cancel your appointment? You can consult another doctor, but there will be no refund for this cancellation.",
                        color = Color.Black,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val id = cancelConfirmAppointmentId ?: return@Button
                            cancelConfirmAppointmentId = null
                            viewModel.cancelAppointment(id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    ) {
                        Text("Yes, Cancel", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { cancelConfirmAppointmentId = null }) {
                        Text("Keep Appointment", color = Color.Black)
                    }
                },
            )
        }
    }
}

@Composable
private fun AppointmentCard(
    appointment: Appointment,
    isCancelling: Boolean,
    isStarting: Boolean = false,
    isWaitingForDoctor: Boolean = false,
    secondsRemaining: Int = 0,
    onCancel: () -> Unit,
    onStartConsultation: () -> Unit = {},
) {
    val now = System.currentTimeMillis()
    val scheduledTime = Instant.ofEpochMilli(appointment.scheduledAt)
        .atZone(EAT)
        .format(dateTimeFormatter)

    val statusColor = when (appointment.status) {
        AppointmentStatus.BOOKED -> BrandTeal
        AppointmentStatus.CONFIRMED -> SuccessGreen
        AppointmentStatus.IN_PROGRESS -> BrandTeal
        AppointmentStatus.COMPLETED -> SuccessGreen
        AppointmentStatus.MISSED -> WarningOrange
        AppointmentStatus.CANCELLED -> ErrorRed
        AppointmentStatus.RESCHEDULED -> SubtitleGrey
    }
    val statusText = when (appointment.status) {
        AppointmentStatus.BOOKED -> stringResource(R.string.appointments_status_booked)
        AppointmentStatus.CONFIRMED -> stringResource(R.string.appointments_status_confirmed)
        AppointmentStatus.IN_PROGRESS -> stringResource(R.string.appointments_status_in_progress)
        AppointmentStatus.COMPLETED -> stringResource(R.string.appointments_status_completed)
        AppointmentStatus.MISSED -> stringResource(R.string.appointments_status_missed)
        AppointmentStatus.CANCELLED -> stringResource(R.string.appointments_status_cancelled)
        AppointmentStatus.RESCHEDULED -> stringResource(R.string.appointments_status_rescheduled)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scheduledTime,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.appointments_duration_service_format, appointment.durationMinutes, appointment.serviceType),
                        fontSize = 13.sp,
                        color = SubtitleGrey,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                    )
                }
            }

            if (appointment.chiefComplaint.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = appointment.chiefComplaint,
                    fontSize = 13.sp,
                    color = SubtitleGrey,
                    maxLines = 2,
                )
            }

            // Rescheduled indicator
            if (appointment.rescheduledFrom != null) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF0F9FF),
                ) {
                    Text(
                        text = stringResource(R.string.appointments_rescheduled_note),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        color = BrandTeal,
                    )
                }
            }

            // Waiting for doctor response — countdown
            if (isWaitingForDoctor) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandTeal.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = BrandTeal,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Waiting for doctor... ${secondsRemaining}s",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTeal,
                        )
                    }
                }
            }

            // Start Consultation button — shown when appointment time has arrived or is within 5 minutes
            val fiveMinutesMs = 5 * 60 * 1000L
            val canStart = !isWaitingForDoctor &&
                appointment.status in listOf(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED, AppointmentStatus.MISSED) &&
                appointment.scheduledAt <= now + fiveMinutesMs &&
                appointment.consultationId == null
            if (canStart) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onStartConsultation,
                    enabled = !isStarting,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isStarting) "Requesting Doctor..." else "Start Consultation",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            // Cancel button for upcoming appointments (only before time arrives)
            if (appointment.status in listOf(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED) && !canStart) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isCancelling,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = ErrorRed,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isCancelling) stringResource(R.string.appointments_cancelling) else stringResource(R.string.appointments_cancel),
                        fontSize = 13.sp,
                        color = ErrorRed,
                    )
                }
            }
        }
    }
}

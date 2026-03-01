package com.esiri.esiriplus.feature.doctor.screen

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.Appointment
import com.esiri.esiriplus.core.domain.model.AppointmentStatus
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorAppointmentTab
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorAppointmentsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF374151)
private val SuccessGreen = Color(0xFF16A34A)
private val WarningOrange = Color(0xFFEA580C)
private val ErrorRed = Color(0xFFDC2626)

private val EAT = ZoneId.of("Africa/Nairobi")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm", Locale.ENGLISH)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

@Composable
fun DoctorAppointmentsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorAppointmentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FFFE)),
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onBack),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "Back", fontSize = 14.sp, color = BrandTeal)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Appointments",
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DoctorAppointmentTab.entries.forEach { tab ->
                val isSelected = uiState.selectedTab == tab
                val label = when (tab) {
                    DoctorAppointmentTab.TODAY -> "Today"
                    DoctorAppointmentTab.UPCOMING -> "Upcoming"
                    DoctorAppointmentTab.MISSED -> "Missed"
                }
                val count = when (tab) {
                    DoctorAppointmentTab.TODAY -> uiState.todayAppointments.size
                    DoctorAppointmentTab.UPCOMING -> uiState.upcomingAppointments.size
                    DoctorAppointmentTab.MISSED -> uiState.missedAppointments.size
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
                        text = "$label ($count)",
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .fillMaxWidth(),
                        fontSize = 13.sp,
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandTeal)
            }
        } else {
            val appointments = when (uiState.selectedTab) {
                DoctorAppointmentTab.TODAY -> uiState.todayAppointments
                DoctorAppointmentTab.UPCOMING -> uiState.upcomingAppointments
                DoctorAppointmentTab.MISSED -> uiState.missedAppointments
            }

            if (appointments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No ${uiState.selectedTab.name.lowercase()} appointments",
                        fontSize = 16.sp,
                        color = SubtitleGrey,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(appointments, key = { it.appointmentId }) { appointment ->
                        DoctorAppointmentCard(
                            appointment = appointment,
                            isRescheduling = uiState.isRescheduling == appointment.appointmentId,
                            onReschedule = { viewModel.showRescheduleDialog(appointment.appointmentId) },
                        )
                    }
                }
            }
        }

        // Error
        if (uiState.errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ErrorRed.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clickable { viewModel.dismissError() },
            ) {
                Text(
                    text = uiState.errorMessage ?: "",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = ErrorRed,
                )
            }
        }
    }
}

@Composable
private fun DoctorAppointmentCard(
    appointment: Appointment,
    isRescheduling: Boolean,
    onReschedule: () -> Unit,
) {
    val scheduledTime = Instant.ofEpochMilli(appointment.scheduledAt)
        .atZone(EAT)

    val (statusColor, statusText) = when (appointment.status) {
        AppointmentStatus.BOOKED -> BrandTeal to "Booked"
        AppointmentStatus.CONFIRMED -> SuccessGreen to "Confirmed"
        AppointmentStatus.IN_PROGRESS -> BrandTeal to "In Progress"
        AppointmentStatus.COMPLETED -> SuccessGreen to "Completed"
        AppointmentStatus.MISSED -> WarningOrange to "Missed"
        AppointmentStatus.CANCELLED -> ErrorRed to "Cancelled"
        AppointmentStatus.RESCHEDULED -> SubtitleGrey to "Rescheduled"
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
                        text = scheduledTime.format(dateTimeFormatter),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${appointment.durationMinutes} min \u00B7 ${appointment.serviceType}",
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

            // Action buttons
            if (appointment.status in listOf(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED)) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { /* Start session - would create consultation */ },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Start Session", fontSize = 13.sp, color = Color.White)
                    }
                }
            }

            if (appointment.status == AppointmentStatus.MISSED) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onReschedule,
                    enabled = !isRescheduling,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BrandTeal),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isRescheduling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = BrandTeal,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isRescheduling) "Rescheduling..." else "Reschedule",
                        fontSize = 13.sp,
                        color = BrandTeal,
                    )
                }
            }
        }
    }
}

package com.esiri.esiriplus.feature.patient.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.BookAppointmentViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF374151)
private val SuccessGreen = Color(0xFF16A34A)
private val WarningOrange = Color(0xFFEA580C)
private val ErrorRed = Color(0xFFDC2626)
private val RatingAmber = Color(0xFFF59E0B)

private val dayOrder = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

@Composable
fun BookAppointmentScreen(
    onBookingSuccess: (consultationId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookAppointmentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate on success
    LaunchedEffect(uiState.bookingSuccess) {
        uiState.bookingSuccess?.let { consultationId ->
            onBookingSuccess(consultationId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color.White, MintLight))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
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
                    text = "Book Appointment",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Fill in the details to request an appointment",
                    fontSize = 14.sp,
                    color = SubtitleGrey,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            if (uiState.isLoadingDoctor) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = BrandTeal)
                }
            } else {
                // Doctor info card
                DoctorInfoCard(
                    doctorName = uiState.doctorName,
                    specialty = uiState.specialty,
                    averageRating = uiState.averageRating,
                    totalRatings = uiState.totalRatings,
                    isVerified = uiState.isVerified,
                )

                // Slots badge
                SlotsBadge(availableSlots = uiState.availableSlots)

                // Availability schedule
                if (uiState.availabilitySchedule.isNotEmpty()) {
                    AvailabilityScheduleSection(schedule = uiState.availabilitySchedule)
                }

                HorizontalDivider(
                    color = CardBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )

                // Booking form
                BookingForm(
                    chiefComplaint = uiState.chiefComplaint,
                    onChiefComplaintChange = viewModel::updateChiefComplaint,
                    preferredLanguage = uiState.preferredLanguage,
                    onPreferredLanguageChange = viewModel::updatePreferredLanguage,
                    isSubmitting = uiState.isSubmitting,
                    errorMessage = uiState.errorMessage,
                    availableSlots = uiState.availableSlots,
                    onBookAppointment = viewModel::bookAppointment,
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DoctorInfoCard(
    doctorName: String,
    specialty: String,
    averageRating: Double,
    totalRatings: Int,
    isVerified: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Initials circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(BrandTeal.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = doctorName
                        .split(" ")
                        .take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                        .joinToString(""),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandTeal,
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Dr. ${doctorName.split(" ").lastOrNull() ?: doctorName}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    if (isVerified) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = BrandTeal,
                            modifier = Modifier.size(18.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "\u2713",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = specialty,
                    fontSize = 14.sp,
                    color = SubtitleGrey,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = RatingAmber,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", averageRating),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                    )
                    Text(
                        text = " ($totalRatings reviews)",
                        fontSize = 12.sp,
                        color = SubtitleGrey,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotsBadge(availableSlots: Int) {
    val (slotColor, slotText) = when {
        availableSlots < 0 -> BrandTeal to "Loading..."
        availableSlots == 0 -> ErrorRed to "0/10 slots available"
        availableSlots <= 3 -> WarningOrange to "$availableSlots/10 slots available"
        else -> SuccessGreen to "$availableSlots/10 slots available"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = slotColor.copy(alpha = 0.1f),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(slotColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = slotText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = slotColor,
            )
        }
    }
}

@Composable
private fun AvailabilityScheduleSection(
    schedule: Map<String, com.esiri.esiriplus.feature.patient.viewmodel.DayScheduleDto>,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_calendar),
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Availability Schedule",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
            )
        }
        Spacer(Modifier.height(8.dp))

        dayOrder.forEach { day ->
            val daySchedule = schedule[day] ?: schedule[day.replaceFirstChar { it.uppercaseChar() }]
            if (daySchedule != null && daySchedule.enabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = day.replaceFirstChar { it.uppercaseChar() },
                        fontSize = 13.sp,
                        color = Color.Black,
                    )
                    Text(
                        text = "${daySchedule.start} - ${daySchedule.end}",
                        fontSize = 13.sp,
                        color = SubtitleGrey,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingForm(
    chiefComplaint: String,
    onChiefComplaintChange: (String) -> Unit,
    preferredLanguage: String,
    onPreferredLanguageChange: (String) -> Unit,
    isSubmitting: Boolean,
    errorMessage: String?,
    availableSlots: Int,
    onBookAppointment: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        // Chief complaint
        Text(
            text = "What is your chief complaint?",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = chiefComplaint,
            onValueChange = { if (it.length <= 1000) onChiefComplaintChange(it) },
            placeholder = {
                Text(
                    text = "Describe your symptoms or reason for the appointment (min 10 characters)...",
                    fontSize = 14.sp,
                    color = SubtitleGrey.copy(alpha = 0.6f),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            maxLines = 5,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "${chiefComplaint.length}/1000",
                fontSize = 11.sp,
                color = SubtitleGrey,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Preferred language
        Text(
            text = "Preferred Language",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("en" to "English", "sw" to "Swahili").forEach { (code, label) ->
                val isSelected = preferredLanguage == code
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) BrandTeal else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) BrandTeal else CardBorder,
                    ),
                    modifier = Modifier.clickable { onPreferredLanguageChange(code) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color.Black,
                        )
                    }
                }
            }
        }

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ErrorRed.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = ErrorRed,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Book button
        val isFormValid = chiefComplaint.trim().length >= 10 && availableSlots != 0
        Button(
            onClick = onBookAppointment,
            enabled = isFormValid && !isSubmitting,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandTeal,
                disabledContainerColor = BrandTeal.copy(alpha = 0.4f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Booking...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Book Appointment",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

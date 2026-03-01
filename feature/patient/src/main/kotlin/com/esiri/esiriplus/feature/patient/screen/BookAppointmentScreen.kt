package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.esiri.esiriplus.feature.patient.viewmodel.TimeSlot
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF374151)
private val SuccessGreen = Color(0xFF16A34A)
private val ErrorRed = Color(0xFFDC2626)
private val RatingAmber = Color(0xFFF59E0B)

private val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val dateNumFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@Composable
fun BookAppointmentScreen(
    onBookingSuccess: (appointmentId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookAppointmentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate on success
    LaunchedEffect(uiState.bookingSuccess) {
        uiState.bookingSuccess?.let { appointmentId ->
            onBookingSuccess(appointmentId)
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
                    text = "Select a date and time for your appointment",
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
                    inSession = uiState.inSession,
                )

                Spacer(Modifier.height(16.dp))

                // Date picker section
                DatePickerSection(
                    dates = uiState.availableDates,
                    selectedDate = uiState.selectedDate,
                    onDateSelected = viewModel::selectDate,
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    color = CardBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(16.dp))

                // Time slots section
                TimeSlotsSection(
                    isLoading = uiState.isLoadingSlots,
                    timeSlots = uiState.timeSlots,
                    selectedTime = uiState.selectedTime,
                    onTimeSelected = viewModel::selectTime,
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    color = CardBorder,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(16.dp))

                // Complaint field + book button
                BookingFormSection(
                    chiefComplaint = uiState.chiefComplaint,
                    onChiefComplaintChange = viewModel::updateChiefComplaint,
                    selectedDate = uiState.selectedDate,
                    selectedTime = uiState.selectedTime,
                    isSubmitting = uiState.isSubmitting,
                    errorMessage = uiState.errorMessage,
                    onBook = viewModel::bookAppointment,
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
    inSession: Boolean,
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
                Text(text = specialty, fontSize = 14.sp, color = SubtitleGrey)
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
                    if (inSession) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF7ED),
                        ) {
                            Text(
                                text = "In Session",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFEA580C),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePickerSection(
    dates: List<LocalDate>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = selectedDate?.format(monthFormatter) ?: "Select a Date",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dates.forEach { date ->
                val isSelected = date == selectedDate
                val isToday = date == LocalDate.now()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) BrandTeal else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) BrandTeal else if (isToday) BrandTeal.copy(alpha = 0.5f) else CardBorder,
                    ),
                    modifier = Modifier
                        .clickable { onDateSelected(date) }
                        .width(56.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = date.format(dayFormatter),
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else SubtitleGrey,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = date.format(dateNumFormatter),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color.Black,
                        )
                        if (isToday) {
                            Spacer(Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else BrandTeal),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSlotsSection(
    isLoading: Boolean,
    timeSlots: List<TimeSlot>,
    selectedTime: LocalTime?,
    onTimeSelected: (LocalTime) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Available Times",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )
        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandTeal, modifier = Modifier.size(32.dp))
            }
        } else if (timeSlots.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF7ED),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "No available time slots for this date. Try a different day.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    color = Color(0xFFEA580C),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Use a fixed height grid for time slots
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((((timeSlots.size + 3) / 4) * 48).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false,
            ) {
                items(timeSlots) { slot ->
                    val isSelected = slot.time == selectedTime
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isSelected -> BrandTeal
                            !slot.isAvailable -> Color(0xFFF3F4F6)
                            else -> Color.White
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isSelected -> BrandTeal
                                !slot.isAvailable -> Color(0xFFE5E7EB)
                                else -> CardBorder
                            },
                        ),
                        modifier = Modifier.clickable(enabled = slot.isAvailable) {
                            onTimeSelected(slot.time)
                        },
                    ) {
                        Text(
                            text = slot.label,
                            modifier = Modifier.padding(vertical = 10.dp),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                isSelected -> Color.White
                                !slot.isAvailable -> SubtitleGrey.copy(alpha = 0.4f)
                                else -> Color.Black
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingFormSection(
    chiefComplaint: String,
    onChiefComplaintChange: (String) -> Unit,
    selectedDate: LocalDate?,
    selectedTime: LocalTime?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onBook: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Chief Complaint",
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
                    text = "Describe your symptoms or reason for the appointment...",
                    fontSize = 14.sp,
                    color = SubtitleGrey.copy(alpha = 0.6f),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            maxLines = 4,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(text = "${chiefComplaint.length}/1000", fontSize = 11.sp, color = SubtitleGrey)
        }

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
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

        Spacer(Modifier.height(16.dp))

        // Book button
        val timeLabel = selectedTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
        val isFormValid = selectedDate != null && selectedTime != null && chiefComplaint.trim().length >= 10
        Button(
            onClick = onBook,
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
                Text(text = "Booking...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (timeLabel.isNotEmpty()) "Book for $timeLabel" else "Select a Time",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.AvailabilityFilter
import com.esiri.esiriplus.feature.patient.viewmodel.ConsultationRequestViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.FindDoctorViewModel
import java.text.NumberFormat
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF374151)
private val SuccessGreen = Color(0xFF16A34A)
private val WarningOrange = Color(0xFFEA580C)
private val RatingAmber = Color(0xFFF59E0B)

private val categoryDisplayNames = mapOf(
    // Service tier category codes (from navigation params)
    "NURSE" to "Nurse",
    "CLINICAL_OFFICER" to "Clinical Officer",
    "PHARMACIST" to "Pharmacist",
    "GP" to "General Practitioner",
    "SPECIALIST" to "Specialist",
    "PSYCHOLOGIST" to "Psychologist",
    // Postgres enum values (from doctor_profiles.specialty)
    "nurse" to "Nurse",
    "clinical_officer" to "Clinical Officer",
    "pharmacist" to "Pharmacist",
    "gp" to "General Practitioner",
    "specialist" to "Specialist",
    "psychologist" to "Psychologist",
)

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

@Composable
fun FindDoctorScreen(
    servicePriceAmount: Int,
    serviceDurationMinutes: Int,
    onBookAppointment: (doctorId: String) -> Unit,
    onNavigateToConsultation: (consultationId: String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FindDoctorViewModel = hiltViewModel(),
    requestViewModel: ConsultationRequestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val requestState by requestViewModel.uiState.collectAsState()
    val categoryName = categoryDisplayNames[uiState.serviceCategory] ?: uiState.serviceCategory

    // Navigate on accepted consultation
    LaunchedEffect(Unit) {
        requestViewModel.acceptedEvent.collect { event ->
            onNavigateToConsultation(event.consultationId)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color.White, MintLight))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                // Back button
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
                    Text(
                        text = "Back",
                        fontSize = 14.sp,
                        color = BrandTeal,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Title + subtitle
                Text(
                    text = "Find a Doctor",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Browse our verified healthcare professionals",
                    fontSize = 14.sp,
                    color = SubtitleGrey,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                // Location + service badge
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Tanzania badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandTeal.copy(alpha = 0.1f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(BrandTeal),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Tanzania",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandTeal,
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "\u00B7",
                        fontSize = 14.sp,
                        color = SubtitleGrey,
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = categoryName,
                        fontSize = 13.sp,
                        color = SubtitleGrey,
                    )
                }
            }

            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = {
                    Text(
                        text = "Search by name, specialty, or rating...",
                        fontSize = 14.sp,
                        color = SubtitleGrey.copy(alpha = 0.6f),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = SubtitleGrey,
                        modifier = Modifier.size(20.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandTeal,
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                ),
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            // Availability filter chips
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvailabilityFilter.entries.forEach { filter ->
                    val isSelected = uiState.availabilityFilter == filter
                    val label = when (filter) {
                        AvailabilityFilter.ALL -> "All"
                        AvailabilityFilter.ONLINE -> "Online"
                        AvailabilityFilter.OFFLINE -> "Offline"
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) BrandTeal else Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) BrandTeal else CardBorder,
                        ),
                        modifier = Modifier.clickable {
                            viewModel.updateAvailabilityFilter(filter)
                        },
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color.Black,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder, thickness = 1.dp)

            // Doctor list
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = BrandTeal)
                }
            } else if (uiState.filteredDoctors.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(R.drawable.ic_stethoscope),
                            contentDescription = null,
                            tint = BrandTeal.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No doctors found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Try adjusting your search or filters to find available healthcare providers.",
                            fontSize = 14.sp,
                            color = SubtitleGrey,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Countdown overlay (shown when request is active)
                    if (requestState.activeRequestId != null) {
                        item {
                            RequestStatusBanner(
                                status = requestState.status,
                                secondsRemaining = requestState.secondsRemaining,
                                statusMessage = requestState.statusMessage,
                                onDismiss = requestViewModel::dismissStatus,
                            )
                        }
                    }

                    items(uiState.filteredDoctors, key = { it.doctorId }) { doctor ->
                        val isThisDoctorRequested = requestState.activeRequestDoctorId == doctor.doctorId
                        val hasActiveRequest = requestState.activeRequestId != null
                        DoctorCard(
                            doctor = doctor,
                            priceAmount = servicePriceAmount,
                            durationMinutes = serviceDurationMinutes,
                            inSession = doctor.inSession,
                            isRequestActive = hasActiveRequest,
                            isThisDoctorRequested = isThisDoctorRequested,
                            isSending = requestState.isSending && isThisDoctorRequested,
                            secondsRemaining = if (isThisDoctorRequested) requestState.secondsRemaining else 0,
                            onBookAppointment = { onBookAppointment(doctor.doctorId) },
                            onRequestConsultation = {
                                requestViewModel.requestConsultation(
                                    doctorId = doctor.doctorId,
                                    serviceType = uiState.serviceCategory.lowercase(),
                                )
                            },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    // Symptoms entry dialog
    if (requestState.showSymptomsDialog) {
        SymptomsEntryDialog(
            patientAgeGroup = requestState.patientAgeGroup,
            patientSex = requestState.patientSex,
            onConfirm = { symptoms -> requestViewModel.confirmAndSendRequest(symptoms) },
            onDismiss = { requestViewModel.dismissSymptomsDialog() },
        )
    }
}

@Composable
private fun DoctorCard(
    doctor: DoctorProfileEntity,
    priceAmount: Int,
    durationMinutes: Int,
    inSession: Boolean = false,
    isRequestActive: Boolean = false,
    isThisDoctorRequested: Boolean = false,
    isSending: Boolean = false,
    secondsRemaining: Int = 0,
    onBookAppointment: () -> Unit,
    onRequestConsultation: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: photo + name + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Profile photo or initials
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BrandTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = doctor.fullName
                            .split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                            .joinToString(""),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTeal,
                    )
                    // Online indicator dot
                    if (doctor.isAvailable) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Dr. ${doctor.fullName.split(" ").lastOrNull() ?: doctor.fullName}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                        )
                        if (doctor.isVerified) {
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                shape = CircleShape,
                                color = BrandTeal,
                                modifier = Modifier.size(16.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "\u2713",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = categoryDisplayNames[doctor.specialty] ?: doctor.specialty,
                            fontSize = 13.sp,
                            color = SubtitleGrey,
                        )
                        Spacer(Modifier.width(8.dp))
                        // Status badge: Available / In Session / Offline
                        val (badgeText, badgeColor) = when {
                            doctor.isAvailable && !inSession -> "Available" to SuccessGreen
                            doctor.isAvailable && inSession -> "In Session" to WarningOrange
                            else -> "Offline" to SubtitleGrey
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeColor.copy(alpha = 0.1f),
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = badgeColor,
                            )
                        }
                    }
                }
            }

            // Bio snippet
            if (doctor.bio.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = doctor.bio,
                    fontSize = 13.sp,
                    color = SubtitleGrey,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
            }

            // Service tags
            if (doctor.services.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val visibleTags = doctor.services.take(2)
                    val remaining = doctor.services.size - visibleTags.size
                    visibleTags.forEach { service ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF9FAFB),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        ) {
                            Text(
                                text = service,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                color = Color.Black,
                            )
                        }
                    }
                    if (remaining > 0) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BrandTeal.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandTeal.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                text = "+$remaining",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandTeal,
                            )
                        }
                    }
                }
            }

            // Languages
            if (doctor.languages.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_language),
                        contentDescription = null,
                        tint = SubtitleGrey,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = doctor.languages.joinToString(", "),
                        fontSize = 12.sp,
                        color = SubtitleGrey,
                    )
                }
            }

            // Rating + experience
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = RatingAmber,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = String.format("%.1f", doctor.averageRating),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                Text(
                    text = " (${doctor.totalRatings} reviews)",
                    fontSize = 12.sp,
                    color = SubtitleGrey,
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = null,
                    tint = SubtitleGrey,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${doctor.yearsExperience} years experience",
                    fontSize = 12.sp,
                    color = SubtitleGrey,
                )
            }

            // Acceptance rate (always 100% for verified doctors)
            if (doctor.isVerified) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "100% acceptance",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SuccessGreen,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))

            // Bottom row: price + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Consultation fee",
                        fontSize = 11.sp,
                        color = SubtitleGrey,
                    )
                    Text(
                        text = "TSh ${numberFormat.format(priceAmount)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Text(
                        text = "/ $durationMinutes min",
                        fontSize = 11.sp,
                        color = SubtitleGrey,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    // Request Consultation button (only when doctor is available AND not in session)
                    if (doctor.isAvailable && !inSession) {
                        val buttonEnabled = !isRequestActive && !isSending
                        Button(
                            onClick = onRequestConsultation,
                            enabled = buttonEnabled,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandTeal,
                                disabledContainerColor = BrandTeal.copy(alpha = 0.4f),
                            ),
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Sending...", fontSize = 13.sp, color = Color.White)
                            } else if (isThisDoctorRequested) {
                                Text(
                                    text = "Waiting... ${secondsRemaining}s",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                )
                            } else {
                                Text(
                                    text = if (isRequestActive) "Request Active" else "Request Consultation",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                )
                            }
                        }
                    }

                    // Book Appointment button (always available, primary when in session)
                    if (inSession) {
                        Button(
                            onClick = onBookAppointment,
                            enabled = !isRequestActive,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandTeal,
                                disabledContainerColor = BrandTeal.copy(alpha = 0.4f),
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Book Appointment",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onBookAppointment,
                            enabled = !isRequestActive,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = null,
                                tint = if (!isRequestActive) Color.Black else SubtitleGrey,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Book Appointment",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (!isRequestActive) Color.Black else SubtitleGrey,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestStatusBanner(
    status: ConsultationRequestStatus?,
    secondsRemaining: Int,
    statusMessage: String?,
    onDismiss: () -> Unit,
) {
    val (bgColor, borderColor) = when (status) {
        ConsultationRequestStatus.ACCEPTED -> Color(0xFFECFDF5) to SuccessGreen
        ConsultationRequestStatus.REJECTED -> Color(0xFFFEF2F2) to Color(0xFFDC2626)
        ConsultationRequestStatus.EXPIRED -> Color(0xFFFFF7ED) to WarningOrange
        else -> Color(0xFFF0FDFA) to BrandTeal
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (status == ConsultationRequestStatus.PENDING && secondsRemaining > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Waiting for doctor response...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BrandTeal,
                    ) {
                        Text(
                            text = "${secondsRemaining}s",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { secondsRemaining / 60f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BrandTeal,
                    trackColor = BrandTeal.copy(alpha = 0.15f),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = statusMessage ?: "",
                        fontSize = 14.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(1f),
                    )
                    if (status != ConsultationRequestStatus.ACCEPTED) {
                        Text(
                            text = "Dismiss",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = BrandTeal,
                            modifier = Modifier
                                .clickable(onClick = onDismiss)
                                .padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

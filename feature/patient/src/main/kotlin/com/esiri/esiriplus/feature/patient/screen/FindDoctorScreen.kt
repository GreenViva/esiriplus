package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.AvailabilityFilter
import com.esiri.esiriplus.feature.patient.viewmodel.ConsultationRequestViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.FindDoctorViewModel
import java.text.NumberFormat
import java.util.Locale

// --- Cosmic palette ---
private val BrandTeal = Color(0xFF2A9D8F)
private val CosmicDark = Color(0xFF0A1628)
private val GlassWhite = Color(0x30FFFFFF)
private val GlassBorder = Color(0x40FFFFFF)
private val StarGlow = Color(0xFF4DD0E1)
private val SuccessGreen = Color(0xFF34D399)
private val WarningOrange = Color(0xFFFBBF24)
private val RatingAmber = Color(0xFFF59E0B)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFFB0BEC5)
private val CardBackground = Color(0x2A1E3A5F)
private val SheetBackground = Color(0xF00A1628)

@Composable
private fun categoryDisplayName(code: String): String {
    return when (code.lowercase()) {
        "nurse" -> stringResource(R.string.category_nurse)
        "clinical_officer" -> stringResource(R.string.category_clinical_officer)
        "pharmacist" -> stringResource(R.string.category_pharmacist)
        "gp" -> stringResource(R.string.category_gp)
        "specialist" -> stringResource(R.string.category_specialist)
        "psychologist" -> stringResource(R.string.category_psychologist)
        "herbalist" -> stringResource(R.string.category_herbalist)
        else -> code
    }
}

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindDoctorScreen(
    servicePriceAmount: Int,
    serviceDurationMinutes: Int,
    serviceTier: String = "ECONOMY",
    onBack: () -> Unit,
    onBookAppointment: (doctorId: String) -> Unit,
    onNavigateToConsultation: (consultationId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FindDoctorViewModel = hiltViewModel(),
    requestViewModel: ConsultationRequestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val requestState by requestViewModel.uiState.collectAsState()
    val categoryName = categoryDisplayName(uiState.serviceCategory)
    val snackbarHostState = remember { SnackbarHostState() }
    val pullRefreshState = rememberPullToRefreshState()

    // Selected doctor for detail sheet
    var selectedDoctor by remember { mutableStateOf<DoctorProfileEntity?>(null) }

    // Back handler: close detail sheet first, then go back to payment page
    BackHandler(enabled = true) {
        if (selectedDoctor != null) {
            selectedDoctor = null
        } else {
            onBack()
        }
    }

    // Navigate on accepted consultation
    LaunchedEffect(Unit) {
        requestViewModel.acceptedEvent.collect { event ->
            onNavigateToConsultation(event.consultationId)
        }
    }

    // Show error messages
    LaunchedEffect(requestState.errorMessage) {
        val msg = requestState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            requestViewModel.dismissError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── Full-bleed background image ──
        Image(
            painter = painterResource(R.drawable.bg_find_doctor),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // ── Dim overlay ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CosmicDark.copy(alpha = 0.75f),
                            CosmicDark.copy(alpha = 0.55f),
                            CosmicDark.copy(alpha = 0.80f),
                        ),
                    ),
                ),
        )

        // ══════════════════════════════════════
        //  LAYER 1: Doctor grid (photo + name)
        // ══════════════════════════════════════
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header with back button ──
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.find_doctor_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    // Spacer to balance the back button
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.height(4.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.find_doctor_subtitle),
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassWhite)
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StarGlow),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.find_doctor_tanzania),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = StarGlow,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(text = "\u00B7", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(text = categoryName, fontSize = 13.sp, color = TextSecondary)
                }
            }

            // ── Search bar ──
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.find_doctor_search_placeholder),
                        fontSize = 14.sp,
                        color = TextSecondary.copy(alpha = 0.6f),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = StarGlow,
                        modifier = Modifier.size(20.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StarGlow,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassWhite,
                    unfocusedContainerColor = GlassWhite,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = StarGlow,
                ),
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            // ── Filter chips ──
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvailabilityFilter.entries.forEach { filter ->
                    val isSelected = uiState.availabilityFilter == filter
                    val label = when (filter) {
                        AvailabilityFilter.ALL -> stringResource(R.string.find_doctor_filter_all)
                        AvailabilityFilter.ONLINE -> stringResource(R.string.find_doctor_filter_online)
                        AvailabilityFilter.OFFLINE -> stringResource(R.string.find_doctor_filter_offline)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) Brush.horizontalGradient(
                                    listOf(StarGlow, BrandTeal),
                                ) else Brush.horizontalGradient(
                                    listOf(GlassWhite, GlassWhite),
                                ),
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent else GlassBorder,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable { viewModel.updateAvailabilityFilter(filter) },
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) CosmicDark else TextPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)

            // ── Doctor avatar grid ──
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullRefreshState,
                modifier = Modifier.weight(1f),
            ) {
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = StarGlow)
                    }
                } else if (uiState.filteredDoctors.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(R.drawable.ic_stethoscope),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                alpha = 0.5f,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.find_doctor_no_doctors_found),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.find_doctor_no_doctors_hint),
                                fontSize = 14.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(uiState.filteredDoctors, key = { it.doctorId }) { doctor ->
                            DoctorAvatarItem(
                                doctor = doctor,
                                onClick = { selectedDoctor = doctor },
                            )
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════
        //  LAYER 2: Detail sheet (slides up)
        // ══════════════════════════════════════
        AnimatedVisibility(
            visible = selectedDoctor != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(350)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it },
        ) {
            selectedDoctor?.let { doctor ->
                val isThisDoctorRequested = requestState.activeRequestDoctorId == doctor.doctorId
                val hasActiveRequest = requestState.activeRequestId != null
                DoctorDetailSheet(
                    doctor = doctor,
                    priceAmount = servicePriceAmount,
                    durationMinutes = serviceDurationMinutes,
                    inSession = doctor.inSession,
                    isRequestActive = hasActiveRequest,
                    isThisDoctorRequested = isThisDoctorRequested,
                    isSending = requestState.isSending && isThisDoctorRequested,
                    secondsRemaining = if (isThisDoctorRequested) requestState.secondsRemaining else 0,
                    requestStatus = requestState.status,
                    requestStatusMessage = requestState.statusMessage,
                    activeRequestId = requestState.activeRequestId,
                    serviceTier = serviceTier,
                    serviceCategory = uiState.serviceCategory,
                    onBack = { selectedDoctor = null },
                    onBookAppointment = { onBookAppointment(doctor.doctorId) },
                    onRequestConsultation = {
                        requestViewModel.requestConsultation(
                            doctorId = doctor.doctorId,
                            serviceType = uiState.serviceCategory.lowercase(),
                            serviceTier = serviceTier,
                        )
                    },
                    onDismissStatus = requestViewModel::dismissStatus,
                )
            }
        }

        // Error snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFFDC2626),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
            )
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

// ═══════════════════════════════════════════
//  Grid item: profile photo + last name
// ═══════════════════════════════════════════
@Composable
private fun DoctorAvatarItem(
    doctor: DoctorProfileEntity,
    onClick: () -> Unit,
) {
    val lastName = doctor.fullName.split(" ").lastOrNull() ?: doctor.fullName
    val initials = doctor.fullName
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        // Avatar with glow ring
        Box(contentAlignment = Alignment.Center) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (doctor.isAvailable) StarGlow.copy(alpha = 0.4f) else GlassBorder,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            // Profile photo or initials
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CardBackground)
                    .border(
                        2.dp,
                        if (doctor.isAvailable) StarGlow.copy(alpha = 0.7f) else GlassBorder,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!doctor.profilePhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(doctor.profilePhotoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Dr. $lastName",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = initials,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = StarGlow,
                    )
                }
            }

            // Online indicator
            if (doctor.isAvailable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(CosmicDark)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Doctor last name
        Text(
            text = "Dr. $lastName",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Availability hint
        Text(
            text = if (doctor.isAvailable && !doctor.inSession) {
                stringResource(R.string.find_doctor_available)
            } else if (doctor.isAvailable && doctor.inSession) {
                stringResource(R.string.find_doctor_in_session)
            } else {
                stringResource(R.string.find_doctor_offline)
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (doctor.isAvailable && !doctor.inSession) SuccessGreen
            else if (doctor.isAvailable) WarningOrange
            else TextSecondary,
        )
    }
}

// ═══════════════════════════════════════════
//  Detail sheet: full doctor info
// ═══════════════════════════════════════════
@Composable
private fun DoctorDetailSheet(
    doctor: DoctorProfileEntity,
    priceAmount: Int,
    durationMinutes: Int,
    inSession: Boolean,
    isRequestActive: Boolean,
    isThisDoctorRequested: Boolean,
    isSending: Boolean,
    secondsRemaining: Int,
    requestStatus: ConsultationRequestStatus?,
    requestStatusMessage: String?,
    activeRequestId: String?,
    serviceTier: String,
    serviceCategory: String,
    onBack: () -> Unit,
    onBookAppointment: () -> Unit,
    onRequestConsultation: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    val lastName = doctor.fullName.split(" ").lastOrNull() ?: doctor.fullName
    val initials = doctor.fullName
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image (same as grid)
        Image(
            painter = painterResource(R.drawable.bg_find_doctor),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CosmicDark.copy(alpha = 0.65f),
                            CosmicDark.copy(alpha = 0.85f),
                            CosmicDark.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )

        // ── Fixed top bar with back button ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CosmicDark.copy(alpha = 0.9f),
                            CosmicDark.copy(alpha = 0.6f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.find_doctor_filter_all),
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = stringResource(R.string.find_doctor_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(top = 56.dp),
        ) {
            // ── Large avatar + name hero ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Large profile photo
                Box(contentAlignment = Alignment.Center) {
                    // Glow ring
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        StarGlow.copy(alpha = 0.3f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(CircleShape)
                            .background(CardBackground)
                            .border(2.5.dp, StarGlow.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!doctor.profilePhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(doctor.profilePhotoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Dr. $lastName",
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = initials,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = StarGlow,
                            )
                        }
                    }
                    // Online dot
                    if (doctor.isAvailable) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(CosmicDark)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Name + verified badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Dr. ${doctor.fullName}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    if (doctor.isVerified) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(StarGlow),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("\u2713", fontSize = 12.sp, color = CosmicDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Specialty + status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = categoryDisplayName(doctor.specialty),
                        fontSize = 14.sp,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.width(10.dp))
                    val (badgeText, badgeColor) = when {
                        doctor.isAvailable && !inSession -> stringResource(R.string.find_doctor_available) to SuccessGreen
                        doctor.isAvailable && inSession -> stringResource(R.string.find_doctor_in_session) to WarningOrange
                        else -> stringResource(R.string.find_doctor_offline) to TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(0.5.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = badgeColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Info card ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Rating + experience row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // Rating
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = RatingAmber,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", doctor.averageRating),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                )
                            }
                            Text(
                                text = stringResource(R.string.find_doctor_reviews_format, doctor.totalRatings),
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(GlassBorder),
                        )

                        // Experience
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${doctor.yearsExperience}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Text(
                                text = stringResource(R.string.find_doctor_years_experience_format, doctor.yearsExperience),
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(GlassBorder),
                        )

                        // Acceptance
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (doctor.isVerified) "100%" else "-",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (doctor.isVerified) SuccessGreen else TextSecondary,
                            )
                            Text(
                                text = stringResource(R.string.find_doctor_acceptance),
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Bio ──
            if (doctor.bio.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBackground)
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "About",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StarGlow,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = doctor.bio,
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Services + Languages card ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Services
                    if (doctor.services.isNotEmpty()) {
                        Text(
                            text = "Services",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StarGlow,
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            doctor.services.forEach { service ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(GlassWhite)
                                        .border(0.5.dp, GlassBorder, RoundedCornerShape(14.dp)),
                                ) {
                                    Text(
                                        text = service,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                    )
                                }
                            }
                        }
                    }

                    // Languages
                    if (doctor.languages.isNotEmpty()) {
                        if (doctor.services.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                            Spacer(Modifier.height(14.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_language),
                                contentDescription = null,
                                tint = StarGlow,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = doctor.languages.joinToString(", "),
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Request status banner (if active) ──
            if (activeRequestId != null) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    RequestStatusBanner(
                        status = requestStatus,
                        secondsRemaining = secondsRemaining,
                        statusMessage = requestStatusMessage,
                        onDismiss = onDismissStatus,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Price + action buttons ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Session duration
                    Text(
                        text = stringResource(R.string.find_doctor_per_duration_format, durationMinutes),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                    )

                    Spacer(Modifier.height(16.dp))

                    // Request Consultation button
                    if (doctor.isAvailable && !inSession) {
                        val buttonEnabled = !isRequestActive && !isSending
                        Button(
                            onClick = onRequestConsultation,
                            enabled = buttonEnabled,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(
                                    brush = if (buttonEnabled) Brush.horizontalGradient(
                                        listOf(StarGlow, BrandTeal),
                                    ) else Brush.horizontalGradient(
                                        listOf(StarGlow.copy(alpha = 0.3f), BrandTeal.copy(alpha = 0.3f)),
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                ),
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.find_doctor_sending), fontSize = 15.sp, color = Color.White)
                            } else if (isThisDoctorRequested) {
                                Text(
                                    text = stringResource(R.string.find_doctor_waiting_format, secondsRemaining),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            } else {
                                Text(
                                    text = if (isRequestActive) stringResource(R.string.find_doctor_request_active) else stringResource(R.string.find_doctor_request_consultation),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // Book Appointment button
                    if (inSession) {
                        Button(
                            onClick = onBookAppointment,
                            enabled = !isRequestActive,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(
                                    brush = if (!isRequestActive) Brush.horizontalGradient(
                                        listOf(StarGlow, BrandTeal),
                                    ) else Brush.horizontalGradient(
                                        listOf(StarGlow.copy(alpha = 0.3f), BrandTeal.copy(alpha = 0.3f)),
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.find_doctor_book_appointment),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onBookAppointment,
                            enabled = !isRequestActive,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = null,
                                tint = if (!isRequestActive) TextPrimary else TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.find_doctor_book_appointment),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isRequestActive) TextPrimary else TextSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════
//  Request status banner
// ═══════════════════════════════════════════
@Composable
private fun RequestStatusBanner(
    status: ConsultationRequestStatus?,
    secondsRemaining: Int,
    statusMessage: String?,
    onDismiss: () -> Unit,
) {
    val (bgColor, borderColor) = when (status) {
        ConsultationRequestStatus.ACCEPTED -> SuccessGreen.copy(alpha = 0.15f) to SuccessGreen
        ConsultationRequestStatus.REJECTED -> Color(0xFFDC2626).copy(alpha = 0.15f) to Color(0xFFDC2626)
        ConsultationRequestStatus.EXPIRED -> WarningOrange.copy(alpha = 0.15f) to WarningOrange
        else -> CardBackground to StarGlow
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (status == ConsultationRequestStatus.PENDING && secondsRemaining > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.find_doctor_waiting_response),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(listOf(StarGlow, BrandTeal))),
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
                    color = StarGlow,
                    trackColor = StarGlow.copy(alpha = 0.15f),
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
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (status != ConsultationRequestStatus.ACCEPTED) {
                        Text(
                            text = stringResource(R.string.find_doctor_dismiss),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = StarGlow,
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

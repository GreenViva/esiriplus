package com.esiri.esiriplus.feature.doctor.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.esiri.esiriplus.feature.doctor.R
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorDashboardUiState
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorDashboardViewModel
import com.esiri.esiriplus.feature.doctor.viewmodel.EarningsTransaction
import com.esiri.esiriplus.feature.doctor.viewmodel.IncomingRequestViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val DarkText = Color.Black
private val CardBorder = Color(0xFFE5E7EB)
private val VerifiedBg = Color(0xFFECFDF5)
private val VerifiedBorder = Color(0xFFBBF7D0)
private val SidebarBg = Color.White
private val ContentBg = Color(0xFFF8FFFE)
private val NavItemSelectedBg = Color(0xFFE8F5F3)
private val SubtitleGray = Color.Black
private val SignOutRed = Color(0xFFDC2626)

private enum class DoctorNavItem(val label: String, val iconRes: Int) {
    DASHBOARD("Dashboard", R.drawable.ic_dashboard),
    CONSULTATIONS("Consultations", R.drawable.ic_chat_bubble),
    CHAT("Chat", R.drawable.ic_chat_bubble),
    AVAILABILITY("Availability", R.drawable.ic_calendar),
    PROFILE("Profile", R.drawable.ic_person_outline),
    EARNINGS("Earnings", R.drawable.ic_trending_up),
}

private val TabLabels = listOf("All Consultations", "Availability", "Earnings", "Profile")

@Composable
fun DoctorDashboardScreen(
    onNavigateToConsultations: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToConsultation: (consultationId: String) -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorDashboardViewModel = hiltViewModel(),
    incomingRequestViewModel: IncomingRequestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val incomingRequestState by incomingRequestViewModel.uiState.collectAsState()

    // Navigate when doctor accepts a request and consultation is created
    LaunchedEffect(Unit) {
        incomingRequestViewModel.consultationStarted.collect { event ->
            onNavigateToConsultation(event.consultationId)
        }
    }

    // Incoming consultation request dialog (shown on top of everything)
    IncomingRequestDialog(
        state = incomingRequestState,
        onAccept = incomingRequestViewModel::acceptRequest,
        onReject = incomingRequestViewModel::rejectRequest,
        onDismiss = incomingRequestViewModel::dismissRequest,
    )

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandTeal)
        }
        return
    }

    var selectedNav by remember { mutableStateOf(DoctorNavItem.DASHBOARD) }
    var isSidebarOpen by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out", color = DarkText, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out?", color = DarkText) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.onSignOut()
                    onSignOut()
                }) {
                    Text("Sign Out", color = SignOutRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = DarkText)
                }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Content area (always full width)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ContentBg),
        ) {
            when (selectedNav) {
                DoctorNavItem.DASHBOARD -> DashboardContent(
                    uiState = uiState,
                    onToggleOnline = viewModel::onToggleOnline,
                    onViewAllRequests = onNavigateToConsultations,
                    onOpenSidebar = { isSidebarOpen = true },
                    onSetAvailability = { selectedNav = DoctorNavItem.AVAILABILITY },
                    onNotificationsClick = onNavigateToNotifications,
                )
                DoctorNavItem.CONSULTATIONS -> ConsultationsContent(
                    uiState = uiState,
                    onOpenSidebar = { isSidebarOpen = true },
                )
                DoctorNavItem.CHAT -> ActiveChatsContent(
                    uiState = uiState,
                    onOpenSidebar = { isSidebarOpen = true },
                )
                DoctorNavItem.AVAILABILITY -> AvailabilitySettingsContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenSidebar = { isSidebarOpen = true },
                )
                DoctorNavItem.PROFILE -> ProfileSettingsContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenSidebar = { isSidebarOpen = true },
                )
                DoctorNavItem.EARNINGS -> EarningsContent(
                    uiState = uiState,
                    onOpenSidebar = { isSidebarOpen = true },
                )
            }
        }

        // Sidebar overlay (slides in from left)
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            Row(modifier = Modifier.fillMaxHeight()) {
                SideNavigation(
                    selectedItem = selectedNav,
                    onItemSelected = {
                        selectedNav = it
                        isSidebarOpen = false
                    },
                    doctorName = uiState.doctorName,
                    specialty = uiState.specialty,
                    isVerified = uiState.isVerified,
                    onSignOut = {
                        isSidebarOpen = false
                        showSignOutDialog = true
                    },
                    onClose = { isSidebarOpen = false },
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .shadow(8.dp),
                )

                // Tap outside to close
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable { isSidebarOpen = false },
                )
            }
        }
    }
}

// ─── Side Navigation ────────────────────────────────────────────────────────────

@Composable
private fun SideNavigation(
    selectedItem: DoctorNavItem,
    onItemSelected: (DoctorNavItem) -> Unit,
    doctorName: String,
    specialty: String,
    isVerified: Boolean,
    onSignOut: () -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SidebarBg)
            .padding(vertical = 16.dp),
    ) {
        // Header: stethoscope + Doctor Portal + close arrow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stethoscope),
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Doctor Portal",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Close menu",
                    tint = DarkText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Verified badge
        if (isVerified) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VerifiedBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_verified_badge),
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Verified",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF16A34A),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Navigation items
        DoctorNavItem.entries.forEach { item ->
            val isSelected = item == selectedItem
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isSelected) Modifier.background(NavItemSelectedBg)
                        else Modifier,
                    )
                    .clickable { onItemSelected(item) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    tint = if (isSelected) BrandTeal else SubtitleGray,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) BrandTeal else DarkText,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Language
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_globe),
                contentDescription = null,
                tint = SubtitleGray,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "English",
                fontSize = 12.sp,
                color = SubtitleGray,
            )
        }

        // Doctor info
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(BrandTeal.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = doctorName.take(1).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandTeal,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = doctorName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (specialty.isNotBlank()) {
                    Text(
                        text = specialty,
                        fontSize = 10.sp,
                        color = SubtitleGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Sign Out
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .clickable { onSignOut() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_logout),
                contentDescription = null,
                tint = SignOutRed,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Sign Out",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = SignOutRed,
            )
        }
    }
}

// ─── Dashboard Content (right panel) ────────────────────────────────────────────

@Composable
private fun DashboardContent(
    uiState: DoctorDashboardUiState,
    onToggleOnline: () -> Unit,
    onViewAllRequests: () -> Unit,
    onOpenSidebar: () -> Unit,
    onSetAvailability: () -> Unit,
    onNotificationsClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar
        TopBar(
            doctorName = uiState.doctorName,
            isOnline = uiState.isOnline,
            isVerified = uiState.isVerified,
            onToggleOnline = onToggleOnline,
            onOpenSidebar = onOpenSidebar,
            onNotificationsClick = onNotificationsClick,
        )

        // Status banners
        Spacer(modifier = Modifier.height(12.dp))
        when {
            uiState.isVerified -> VerifiedBanner(modifier = Modifier.padding(horizontal = 12.dp))
            uiState.rejectionReason != null -> RejectedBanner(
                reason = uiState.rejectionReason,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            else -> PendingReviewBanner(modifier = Modifier.padding(horizontal = 12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stats grid (2 columns)
        StatsGrid(uiState = uiState, modifier = Modifier.padding(horizontal = 12.dp))

        Spacer(modifier = Modifier.height(16.dp))

        // Today's Availability
        AvailabilitySection(
            isAvailable = uiState.isAvailable,
            onSetAvailability = onSetAvailability,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Pending Requests
        PendingRequestsSection(
            count = uiState.pendingRequests,
            onViewAll = onViewAllRequests,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TopBar(
    doctorName: String,
    isOnline: Boolean,
    isVerified: Boolean,
    onToggleOnline: () -> Unit,
    onOpenSidebar: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Row 1: Menu + Welcome + Notifications + Online toggle (or Under Review chip)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSidebar,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open menu",
                    tint = DarkText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Welcome back,",
                fontSize = 12.sp,
                color = DarkText,
                modifier = Modifier.weight(1f),
            )

            // Notification bell
            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = DarkText,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            if (isVerified) {
                // Online toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color(0xFF22C55E), CircleShape),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "Online",
                        fontSize = 12.sp,
                        color = DarkText,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = isOnline,
                        onCheckedChange = { onToggleOnline() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = BrandTeal,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = CardBorder,
                            uncheckedThumbColor = Color.White,
                        ),
                    )
                }
            } else {
                // Under Review chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF7ED))
                        .border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Under Review",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEA580C),
                    )
                }
            }
        }

        // Row 2: Doctor name
        Text(
            text = doctorName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VerifiedBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, VerifiedBorder, RoundedCornerShape(10.dp))
            .background(VerifiedBg)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(BrandTeal, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "Verified Doctor",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )
            Text(
                text = "Your profile is verified. You can now accept patient consultations.",
                fontSize = 12.sp,
                color = DarkText,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun PendingReviewBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(10.dp))
            .background(Color(0xFFFFF7ED))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFFF97316), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "Application Under Review",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )
            Text(
                text = "Your application is under review. This typically takes 1\u20135 business days. You\u2019ll be notified once approved.",
                fontSize = 12.sp,
                color = DarkText,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun RejectedBanner(reason: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(10.dp))
            .background(Color(0xFFFEF2F2))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFFDC2626), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "Application Not Approved",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )
            Text(
                text = reason,
                fontSize = 12.sp,
                color = DarkText,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Please review the reason and resubmit your credentials.",
                fontSize = 11.sp,
                color = Color(0xFF991B1B),
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun TabChipRow(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TabLabels.forEachIndexed { index, label ->
            val isSelected = index == selectedTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) Modifier.background(BrandTeal)
                        else Modifier.border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                    )
                    .clickable { selectedTab = index }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White else DarkText,
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(uiState: DoctorDashboardUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Row 1: Pending Requests + Active Consultations
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                value = "${uiState.pendingRequests}",
                label = "Pending Requests",
                iconColor = Color(0xFFF59E0B),
                iconBg = Color(0xFFFEF3C7),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "${uiState.activeConsultations}",
                label = "Active Consultations",
                iconColor = BrandTeal,
                iconBg = Color(0xFFE0F2FE),
                modifier = Modifier.weight(1f),
            )
        }
        // Row 2: Today's Earnings + Total Patients
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                value = uiState.todaysEarnings,
                label = "Today's Earnings",
                iconColor = Color(0xFF22C55E),
                iconBg = Color(0xFFDCFCE7),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "${uiState.totalPatients}",
                label = "Total Patients",
                iconColor = Color(0xFF3B82F6),
                iconBg = Color(0xFFDBEAFE),
                modifier = Modifier.weight(1f),
            )
        }
        // Row 3: Acceptance Rate (single card)
        StatCard(
            value = uiState.acceptanceRate,
            label = "Acceptance Rate",
            iconColor = BrandTeal,
            iconBg = Color(0xFFD1FAE5),
            modifier = Modifier.fillMaxWidth(0.48f),
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    iconColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(iconColor, CircleShape),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = DarkText,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun AvailabilitySection(
    isAvailable: Boolean,
    onSetAvailability: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = DarkText,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Today's Availability",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = CardBorder,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isAvailable) "Available today" else "Not available today",
                fontSize = 13.sp,
                color = SubtitleGray,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .clickable { onSetAvailability() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Set Availability",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkText,
                )
            }
        }
    }
}

@Composable
private fun PendingRequestsSection(
    count: Int,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Pending Requests",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "View All",
                    fontSize = 12.sp,
                    color = DarkText,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = DarkText,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = CardBorder,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (count == 0) "No pending requests" else "$count pending request(s)",
                fontSize = 13.sp,
                color = SubtitleGray,
            )
        }
    }
}

// ─── Consultations Content ──────────────────────────────────────────────────────

private enum class ConsultationTab(val label: String) {
    PENDING("Pending"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
}

@Composable
private fun ConsultationsContent(
    uiState: DoctorDashboardUiState,
    onOpenSidebar: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(ConsultationTab.PENDING) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        // Header with menu button
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onOpenSidebar,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open menu",
                    tint = DarkText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Consultations",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                )
                Text(
                    text = "Manage your patient consultations",
                    fontSize = 12.sp,
                    color = BrandTeal,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ConsultationTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) Modifier
                                .background(Color.White)
                                .border(1.dp, DarkText, RoundedCornerShape(20.dp))
                            else Modifier.border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = tab.label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = DarkText,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content for selected tab
        val consultations = when (selectedTab) {
            ConsultationTab.PENDING -> uiState.pendingConsultations
            ConsultationTab.ACTIVE -> uiState.activeConsultationsList
            ConsultationTab.COMPLETED -> uiState.completedConsultations
            ConsultationTab.CANCELLED -> uiState.cancelledConsultations
        }

        if (consultations.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = CardBorder,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No ${selectedTab.label.lowercase()} consultations",
                    fontSize = 14.sp,
                    color = DarkText,
                )
            }
        } else {
            // Consultation list
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                consultations.forEach { consultation ->
                    ConsultationCard(consultation = consultation)
                }
            }
        }
    }
}

@Composable
private fun ConsultationCard(
    consultation: com.esiri.esiriplus.core.database.entity.ConsultationEntity,
) {
    val statusColor = when (consultation.status) {
        "PENDING" -> Color(0xFFF59E0B)
        "ACTIVE" -> BrandTeal
        "COMPLETED" -> Color(0xFF22C55E)
        "CANCELLED" -> Color(0xFFDC2626)
        else -> DarkText
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = consultation.serviceType.replace("_", " ")
                    .replaceFirstChar { it.uppercase() },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkText,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = consultation.status.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Fee: TSh ${consultation.consultationFee}",
            fontSize = 12.sp,
            color = DarkText,
        )
        Text(
            text = "Duration: ${consultation.sessionDurationMinutes} min",
            fontSize = 12.sp,
            color = DarkText,
        )
    }
}

// ─── Availability Settings Content ──────────────────────────────────────────────

private val TimeOptions = (0..23).flatMap { h ->
    listOf("${h.toString().padStart(2, '0')}:00", "${h.toString().padStart(2, '0')}:30")
}

@Composable
private fun AvailabilitySettingsContent(
    uiState: DoctorDashboardUiState,
    viewModel: DoctorDashboardViewModel,
    onOpenSidebar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSidebar,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open menu",
                    tint = DarkText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Availability Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                )
                Text(
                    text = "Set your weekly consultation hours",
                    fontSize = 12.sp,
                    color = BrandTeal,
                )
            }
            // Save button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandTeal)
                    .clickable { viewModel.saveAvailability() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (uiState.availabilitySaved) "Saved" else "Save Changes",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Quick Presets
            Text(
                text = "Quick Presets",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                PresetChip("Mon-Fri (9 AM - 5 PM)") { viewModel.applyPresetMonFri() }
                PresetChip("Every Day (9 AM - 5 PM)") { viewModel.applyPresetEveryDay() }
                PresetChip("Clear All") { viewModel.clearAllAvailability() }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Weekly Schedule
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = BrandTeal,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Weekly Schedule",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Day rows
            val days = uiState.weeklySchedule.toMap()
            days.forEach { (dayName, schedule) ->
                DayScheduleRow(
                    dayName = dayName,
                    schedule = schedule,
                    onToggle = { viewModel.onDayToggled(dayName) },
                    onStartChanged = { viewModel.onDayStartChanged(dayName, it) },
                    onEndChanged = { viewModel.onDayEndChanged(dayName, it) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = DarkText,
        )
    }
}

@Composable
private fun DayScheduleRow(
    dayName: String,
    schedule: com.esiri.esiriplus.feature.doctor.viewmodel.DaySchedule,
    onToggle: () -> Unit,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Day name
        Text(
            text = dayName.take(3),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkText,
            modifier = Modifier.width(36.dp),
        )

        // Toggle
        Switch(
            checked = schedule.enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = BrandTeal,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = CardBorder,
                uncheckedThumbColor = Color.White,
            ),
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (schedule.enabled) {
            // Start time dropdown
            TimeDropdown(
                value = schedule.start,
                onValueChanged = onStartChanged,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "\u2014",
                fontSize = 13.sp,
                color = DarkText,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            // End time dropdown
            TimeDropdown(
                value = schedule.end,
                onValueChanged = onEndChanged,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = "Off",
                fontSize = 13.sp,
                color = CardBorder,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDropdown(
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = value,
                fontSize = 13.sp,
                color = DarkText,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.height(200.dp),
        ) {
            TimeOptions.forEach { time ->
                DropdownMenuItem(
                    text = { Text(time, fontSize = 13.sp) },
                    onClick = {
                        onValueChanged(time)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ─── Profile Settings Content ───────────────────────────────────────────────────

private val CommonLanguages = listOf("English", "Swahili")
private val OtherLanguages = listOf(
    "Afrikaans", "Amharic", "Arabic", "Bengali", "Chichewa",
    "Chinese", "Dutch", "French", "German", "Hindi",
    "Italian", "Japanese", "Korean", "Malay", "Portuguese",
    "Russian", "Spanish", "Thai", "Turkish", "Urdu",
)
private val ServiceOptions = listOf(
    "General Health", "Mental Health",
    "Sexual & Reproductive Health", "Chronic Illness Management",
)
private val CountryOptions = listOf(
    "Tanzania", "Kenya", "Uganda", "Rwanda", "Burundi",
    "Ethiopia", "Nigeria", "South Africa", "Ghana", "Egypt",
)

@Composable
private fun ProfileSettingsContent(
    uiState: DoctorDashboardUiState,
    viewModel: DoctorDashboardViewModel,
    onOpenSidebar: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = BrandTeal,
        unfocusedBorderColor = CardBorder,
        focusedTextColor = DarkText,
        unfocusedTextColor = DarkText,
        cursorColor = BrandTeal,
        focusedLabelColor = BrandTeal,
        unfocusedLabelColor = SubtitleGray,
        disabledTextColor = DarkText,
        disabledBorderColor = CardBorder,
        disabledLabelColor = SubtitleGray,
        disabledContainerColor = Color(0xFFF5F5F5),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSidebar,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open menu",
                    tint = DarkText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Profile Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                )
                Text(
                    text = "View your profile and update preferences",
                    fontSize = 12.sp,
                    color = BrandTeal,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            uri?.let { viewModel.onProfilePhotoSelected(it) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Profile Photo ────────────────────────────────────────
            ProfileSectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person_outline),
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Profile Photo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { photoPickerLauncher.launch("image/*") }
                        .padding(4.dp),
                ) {
                    // Avatar — show actual photo if available
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.profileUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = BrandTeal,
                                strokeWidth = 2.dp,
                            )
                        } else if (!uiState.profilePhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = uiState.profilePhotoUrl,
                                contentDescription = "Profile photo",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = uiState.doctorName.take(2).uppercase(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandTeal,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Tap to change photo", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BrandTeal)
                        Text("JPG, PNG, max 5MB", fontSize = 11.sp, color = SubtitleGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Personal Information ─────────────────────────────────
            ProfileSectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person_outline),
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Personal Information", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                }
                Text(
                    text = "Set during registration",
                    fontSize = 11.sp,
                    color = BrandTeal,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(start = 22.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Full Name + Phone (read-only)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.doctorName,
                        onValueChange = {},
                        label = { Text("Full Name") },
                        enabled = false,
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.profilePhone,
                        onValueChange = {},
                        label = { Text("Phone Number") },
                        enabled = false,
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Email (read-only)
                OutlinedTextField(
                    value = uiState.profileEmail,
                    onValueChange = {},
                    label = { Text("Email") },
                    enabled = false,
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Professional Information ─────────────────────────────
            ProfileSectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_verified_badge),
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Professional Information", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                }
                Text(
                    text = "Set during registration",
                    fontSize = 11.sp,
                    color = BrandTeal,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(start = 22.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Specialty + License (read-only)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.profileSpecialty,
                        onValueChange = {},
                        label = { Text("Specialty") },
                        enabled = false,
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.profileLicenseNumber,
                        onValueChange = {},
                        label = { Text("License Number") },
                        enabled = false,
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Years of Experience (read-only, auto-updates each year)
                OutlinedTextField(
                    value = uiState.profileYearsExperience,
                    onValueChange = {},
                    label = { Text("Years of Experience") },
                    enabled = false,
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(0.48f),
                )
                Text(
                    text = "Updates automatically each year",
                    fontSize = 11.sp,
                    color = BrandTeal,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Professional Bio (read-only)
                Text("Professional Bio", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DarkText)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.profileBio,
                    onValueChange = {},
                    enabled = false,
                    minLines = 3,
                    maxLines = 5,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Location & Languages ─────────────────────────────────
            ProfileSectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_globe),
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Location & Languages", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Country (read-only)
                OutlinedTextField(
                    value = uiState.profileCountry,
                    onValueChange = {},
                    label = { Text("Country") },
                    enabled = false,
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(0.48f),
                )
                Text(
                    text = "Set during registration",
                    fontSize = 11.sp,
                    color = BrandTeal,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Languages (editable)
                Text("Languages You Speak", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DarkText)
                Spacer(modifier = Modifier.height(4.dp))

                Text("COMMON LANGUAGES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SubtitleGray)
                Spacer(modifier = Modifier.height(4.dp))
                WrappingRow(horizontalSpacing = 6.dp, verticalSpacing = 6.dp) {
                    CommonLanguages.forEach { lang ->
                        SelectableChip(
                            label = lang,
                            isSelected = uiState.profileLanguages.contains(lang),
                            onClick = { viewModel.onProfileLanguageToggled(lang) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("OTHER LANGUAGES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SubtitleGray)
                Spacer(modifier = Modifier.height(4.dp))
                WrappingRow(horizontalSpacing = 6.dp, verticalSpacing = 6.dp) {
                    OtherLanguages.forEach { lang ->
                        SelectableChip(
                            label = lang,
                            isSelected = uiState.profileLanguages.contains(lang),
                            onClick = { viewModel.onProfileLanguageToggled(lang) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${uiState.profileLanguages.size} language(s) selected",
                    fontSize = 11.sp,
                    color = BrandTeal,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Services Offered ──────────────────────────────────────
            ProfileSectionCard {
                Text("Services Offered", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Spacer(modifier = Modifier.height(8.dp))
                WrappingRow(horizontalSpacing = 6.dp, verticalSpacing = 6.dp) {
                    ServiceOptions.forEach { service ->
                        SelectableChip(
                            label = service,
                            isSelected = uiState.profileServices.contains(service),
                            onClick = { viewModel.onProfileServiceToggled(service) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Available for Consultations ───────────────────────────
            ProfileSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Available for Consultations", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Text(
                            "Toggle off to temporarily hide your profile from patients",
                            fontSize = 11.sp,
                            color = SubtitleGray,
                        )
                    }
                    Switch(
                        checked = uiState.profileAvailableForConsultations,
                        onCheckedChange = { viewModel.onProfileAvailableToggled() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = BrandTeal,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = CardBorder,
                            uncheckedThumbColor = Color.White,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Changes button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandTeal)
                    .clickable { viewModel.saveProfile() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            uiState.profileSaved && uiState.profileSyncFailed -> "Saved Locally"
                            uiState.profileSaved -> "Saved"
                            else -> "Save Changes"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            if (uiState.profileSyncFailed) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Changes saved locally but could not sync to server. They will sync on next launch.",
                    fontSize = 11.sp,
                    color = Color(0xFFD97706),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileSectionCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
private fun SelectableChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isSelected) Modifier
                    .background(Color.White)
                    .border(1.5.dp, BrandTeal, RoundedCornerShape(20.dp))
                else Modifier.border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(BrandTeal, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(1.dp, CardBorder, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = DarkText,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun ProfileDropdown(
    value: String,
    options: List<String>,
    onValueChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_globe),
                contentDescription = null,
                tint = SubtitleGray,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = value, fontSize = 13.sp, color = DarkText, modifier = Modifier.weight(1f))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.height(200.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        onValueChanged(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ─── Active Chats Content ───────────────────────────────────────────────────────

@Composable
private fun ActiveChatsContent(
    uiState: DoctorDashboardUiState,
    onOpenSidebar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        // Header with menu button
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onOpenSidebar,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open menu",
                    tint = DarkText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Chat",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                )
                Text(
                    text = "Active consultations",
                    fontSize = 12.sp,
                    color = BrandTeal,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.activeConsultationsList.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chat_bubble),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = CardBorder,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No active consultations",
                    fontSize = 14.sp,
                    color = DarkText,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Active consultations will appear here",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                )
            }
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                uiState.activeConsultationsList.forEach { consultation ->
                    ConsultationCard(consultation = consultation)
                }
            }
        }
    }
}

// ─── Earnings Content ───────────────────────────────────────────────────────────

@Composable
private fun EarningsContent(
    uiState: DoctorDashboardUiState,
    onOpenSidebar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar with menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSidebar,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open menu",
                    tint = DarkText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "Earnings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                )
                Text(
                    text = "Track your consultation earnings",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats grid (2x2)
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EarningsStatCard(
                    value = uiState.totalEarnings,
                    label = "Total Earnings",
                    iconRes = R.drawable.ic_dashboard,
                    iconColor = Color(0xFF22C55E),
                    iconBg = Color(0xFFDCFCE7),
                    modifier = Modifier.weight(1f),
                )
                EarningsStatCard(
                    value = uiState.thisMonthEarnings,
                    label = "This Month",
                    iconRes = R.drawable.ic_calendar,
                    iconColor = Color(0xFF22C55E),
                    iconBg = Color(0xFFDCFCE7),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EarningsStatCard(
                    value = uiState.lastMonthEarnings,
                    label = "Last Month",
                    iconRes = R.drawable.ic_trending_up,
                    iconColor = Color(0xFF3B82F6),
                    iconBg = Color(0xFFDBEAFE),
                    modifier = Modifier.weight(1f),
                )
                EarningsStatCard(
                    value = uiState.pendingPayout,
                    label = "Pending Payout",
                    iconRes = R.drawable.ic_trending_up,
                    iconColor = Color(0xFFF59E0B),
                    iconBg = Color(0xFFFEF3C7),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Payout Information card
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(10.dp))
                .background(Color(0xFFFFFBEB))
                .padding(14.dp),
        ) {
            Text(
                text = "Payout Information",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD97706),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Payouts are processed automatically at the end of each month via M-Pesa. Ensure your phone number is correct in your profile settings.",
                fontSize = 12.sp,
                color = Color(0xFF92400E),
                lineHeight = 18.sp,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recent Transactions section
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(14.dp),
        ) {
            Text(
                text = "Recent Transactions",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkText,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.recentTransactions.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dashboard),
                        contentDescription = null,
                        tint = Color(0xFFD1D5DB),
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No transactions yet",
                        fontSize = 13.sp,
                        color = Color(0xFF9CA3AF),
                    )
                }
            } else {
                uiState.recentTransactions.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.patientName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkText,
                            )
                            Text(
                                text = tx.date,
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = tx.amount,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText,
                            )
                            Text(
                                text = tx.status,
                                fontSize = 11.sp,
                                color = if (tx.status == "Completed") Color(0xFF22C55E) else Color(0xFFF59E0B),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun EarningsStatCard(
    value: String,
    label: String,
    iconRes: Int,
    iconColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = DarkText,
            lineHeight = 14.sp,
        )
    }
}

// ─── Section content with menu button for unimplemented sections ────────────────

@Composable
private fun SectionContent(title: String, onOpenSidebar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onOpenSidebar,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open menu",
                    tint = DarkText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Coming Soon",
                fontSize = 16.sp,
                color = DarkText,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─── Wrapping Row (FlowRow replacement) ─────────────────────────────────────────

@Composable
private fun WrappingRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 0.dp,
    verticalSpacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val hSpacingPx = horizontalSpacing.roundToPx()
        val vSpacingPx = verticalSpacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }

        var currentX = 0
        var currentY = 0
        var rowHeight = 0

        val positions = placeables.map { placeable ->
            if (currentX + placeable.width > constraints.maxWidth && currentX > 0) {
                currentX = 0
                currentY += rowHeight + vSpacingPx
                rowHeight = 0
            }
            val pos = Pair(currentX, currentY)
            currentX += placeable.width + hSpacingPx
            rowHeight = maxOf(rowHeight, placeable.height)
            pos
        }

        val totalHeight = if (placeables.isEmpty()) 0 else currentY + rowHeight

        layout(constraints.maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(positions[index].first, positions[index].second)
            }
        }
    }
}

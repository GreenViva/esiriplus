package com.esiri.esiriplus.feature.doctor.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.esiri.esiriplus.core.domain.model.Appointment
import com.esiri.esiriplus.core.ui.LanguageSwitchButton
import com.esiri.esiriplus.feature.doctor.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorDashboardUiState
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorDashboardViewModel
import com.esiri.esiriplus.feature.doctor.viewmodel.EarningsTransaction
import com.esiri.esiriplus.feature.doctor.viewmodel.IncomingRequestViewModel
import com.esiri.esiriplus.feature.doctor.viewmodel.ServiceCommand

private val BrandTeal = Color(0xFF2A9D8F)
private val VerifiedBg = Color(0xFFECFDF5)
private val VerifiedBorder = Color(0xFFBBF7D0)
private val NavItemSelectedBg = Color(0xFFE8F5F3)
private val SignOutRed = Color(0xFFDC2626)

private enum class DoctorNavItem(val labelRes: Int, val iconRes: Int) {
    DASHBOARD(R.string.nav_dashboard, R.drawable.ic_dashboard),
    CONSULTATIONS(R.string.nav_consultations, R.drawable.ic_chat_bubble),
    CHAT(R.string.nav_chat, R.drawable.ic_chat_bubble),
    AVAILABILITY(R.string.nav_availability, R.drawable.ic_calendar),
    PROFILE(R.string.nav_profile, R.drawable.ic_person_outline),
    EARNINGS(R.string.nav_earnings, R.drawable.ic_trending_up),
}

private val TabLabelRes = listOf(R.string.tab_all_consultations, R.string.tab_availability, R.string.tab_earnings, R.string.tab_profile)

@Composable
fun DoctorDashboardScreen(
    onNavigateToConsultations: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToConsultation: (consultationId: String) -> Unit = {},
    onNavigateToAppointments: () -> Unit = {},
    onNavigateToAvailabilitySettings: () -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorDashboardViewModel = hiltViewModel(),
    incomingRequestViewModel: IncomingRequestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val incomingRequestState by incomingRequestViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Show toast when doctor tries to toggle while suspended
    LaunchedEffect(uiState.suspensionMessage) {
        uiState.suspensionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearSuspensionMessage()
        }
    }

    // Request POST_NOTIFICATIONS permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not — FCM will work either way, just no system tray */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ── Retry overlay bubble after user grants permission and returns ──────────
    var sentToOverlaySettings by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && sentToOverlaySettings) {
                sentToOverlaySettings = false
                // Send RETRY_BUBBLE intent to running service
                if (Settings.canDrawOverlays(context)) {
                    try {
                        val retryIntent = Intent().apply {
                            setClassName(
                                context.packageName,
                                "com.esiri.esiriplus.service.DoctorOnlineService",
                            )
                            action = "com.esiri.esiriplus.service.RETRY_BUBBLE"
                        }
                        context.startService(retryIntent)
                    } catch (e: Exception) {
                        android.util.Log.e("DoctorDashboard", "Failed to retry bubble", e)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Service control: start/stop DoctorOnlineService on toggle ─────────────
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var pendingServiceDoctorId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.serviceCommand.collect { command ->
            when (command) {
                is ServiceCommand.Start -> {
                    // Check overlay permission — if denied, show dialog but start service anyway
                    if (!Settings.canDrawOverlays(context)) {
                        pendingServiceDoctorId = command.doctorId
                        showOverlayPermissionDialog = true
                    }
                    // Start service via Intent (works without overlay — graceful degradation)
                    try {
                        val intent = Intent().apply {
                            setClassName(context.packageName, "com.esiri.esiriplus.service.DoctorOnlineService")
                            action = "com.esiri.esiriplus.service.START"
                            putExtra("doctor_id", command.doctorId)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DoctorDashboard", "Failed to start service", e)
                    }
                }
                is ServiceCommand.Stop -> {
                    try {
                        val intent = Intent().apply {
                            setClassName(context.packageName, "com.esiri.esiriplus.service.DoctorOnlineService")
                            action = "com.esiri.esiriplus.service.STOP"
                        }
                        context.startService(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("DoctorDashboard", "Failed to stop service", e)
                    }
                }
            }
        }
    }

    // Overlay permission dialog
    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = { Text(stringResource(R.string.overlay_dialog_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.overlay_dialog_message),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayPermissionDialog = false
                    sentToOverlaySettings = true
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.overlay_dialog_open_settings), color = BrandTeal, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text(stringResource(R.string.overlay_dialog_not_now), color = MaterialTheme.colorScheme.onSurface)
                }
            },
        )
    }

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

    // ── Banned doctor: immovable full-screen ban notice ────────────────────────
    if (uiState.isBanned) {
        BanNoticeScreen(
            banReason = uiState.banReason,
            bannedAt = uiState.bannedAt,
            onSignOut = onSignOut,
        )
        return
    }

    var selectedNav by remember { mutableStateOf(DoctorNavItem.DASHBOARD) }
    var isSidebarOpen by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.sign_out_dialog_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.sign_out_dialog_message), color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.onSignOut()
                    onSignOut()
                }) {
                    Text(stringResource(R.string.common_sign_out), color = SignOutRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurface)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Persistent banner for active consultation resume
            val activeConsultationId = uiState.activeConsultationToResume
            if (activeConsultationId != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToConsultation(activeConsultationId) },
                    color = BrandTeal,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.dashboard_active_consultation),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(
                                stringResource(R.string.dashboard_tap_to_resume),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            stringResource(R.string.dashboard_resume),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
            when (selectedNav) {
                DoctorNavItem.DASHBOARD -> DashboardContent(
                    uiState = uiState,
                    onToggleOnline = viewModel::onToggleOnline,
                    onViewAllRequests = onNavigateToConsultations,
                    onViewAllAppointments = onNavigateToAppointments,
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
                    onNavigateToConsultation = onNavigateToConsultation,
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp),
    ) {
        // Header: stethoscope + Doctor Portal + close arrow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_stethoscope),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sidebar_doctor_portal),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.common_close_menu),
                    tint = MaterialTheme.colorScheme.onSurface,
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
                    text = stringResource(R.string.sidebar_verified),
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
                    tint = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(item.labelRes),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurface,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.common_english),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (specialty.isNotBlank()) {
                    Text(
                        text = specialty,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = stringResource(R.string.common_sign_out),
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
    onViewAllAppointments: () -> Unit = {},
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
            isSuspended = uiState.suspendedUntil != null,
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

        Spacer(modifier = Modifier.height(12.dp))

        // Upcoming Appointments
        AppointmentsSection(
            todayAppointments = uiState.todayAppointments,
            upcomingAppointments = uiState.upcomingAppointments,
            isLoading = uiState.isLoadingAppointments,
            onViewAll = onViewAllAppointments,
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
    isSuspended: Boolean = false,
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
                    contentDescription = stringResource(R.string.common_open_menu),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.dashboard_welcome_back),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            // Notification bell
            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = stringResource(R.string.dashboard_notifications),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            // Language switch
            LanguageSwitchButton(iconTint = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(4.dp))

            if (isVerified && isSuspended) {
                // Suspended chip — toggle disabled
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_suspended),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFDC2626),
                    )
                }
            } else if (isVerified) {
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
                        text = stringResource(R.string.dashboard_online),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = isOnline,
                        onCheckedChange = { onToggleOnline() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = BrandTeal,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline,
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
                        text = stringResource(R.string.dashboard_under_review),
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
            color = MaterialTheme.colorScheme.onSurface,
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
                text = stringResource(R.string.banner_verified_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.banner_verified_message),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
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
                text = stringResource(R.string.banner_pending_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.banner_pending_message),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
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
                text = stringResource(R.string.banner_rejected_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = reason,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.banner_rejected_resubmit),
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
        TabLabelRes.forEachIndexed { index, labelRes ->
            val isSelected = index == selectedTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) Modifier.background(BrandTeal)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                    )
                    .clickable { selectedTab = index }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(labelRes),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
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
                label = stringResource(R.string.stats_pending_requests),
                iconColor = Color(0xFFF59E0B),
                iconBg = Color(0xFFFEF3C7),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "${uiState.activeConsultations}",
                label = stringResource(R.string.stats_active_consultations),
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
                label = stringResource(R.string.stats_todays_earnings),
                iconColor = Color(0xFF22C55E),
                iconBg = Color(0xFFDCFCE7),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "${uiState.totalPatients}",
                label = stringResource(R.string.stats_total_patients),
                iconColor = Color(0xFF3B82F6),
                iconBg = Color(0xFFDBEAFE),
                modifier = Modifier.weight(1f),
            )
        }
        // Row 3: Acceptance Rate + Appointments
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                value = uiState.acceptanceRate,
                label = stringResource(R.string.stats_acceptance_rate),
                iconColor = BrandTeal,
                iconBg = Color(0xFFD1FAE5),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "${uiState.todayAppointments.size + uiState.upcomingAppointments.size}",
                label = stringResource(R.string.stats_appointments),
                iconColor = Color(0xFF8B5CF6),
                iconBg = Color(0xFFEDE9FE),
                modifier = Modifier.weight(1f),
            )
        }
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
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
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.availability_today_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
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
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isAvailable) stringResource(R.string.availability_available_today) else stringResource(R.string.availability_not_available_today),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable { onSetAvailability() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.availability_set),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.pending_requests_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.common_view_all),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
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
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (count == 0) stringResource(R.string.pending_requests_none) else stringResource(R.string.pending_requests_count, count),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Appointments Section ───────────────────────────────────────────────────────

private val appointmentDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val appointmentTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@Composable
private fun AppointmentsSection(
    todayAppointments: List<Appointment>,
    upcomingAppointments: List<Appointment>,
    isLoading: Boolean,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allAppointments = todayAppointments + upcomingAppointments
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.appointments_upcoming_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.common_view_all),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandTeal, modifier = Modifier.size(24.dp))
            }
        } else if (allAppointments.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.appointments_none),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Show up to 3 appointments as a preview
            allAppointments.take(3).forEach { appointment ->
                AppointmentRow(appointment = appointment)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (allAppointments.size > 3) {
                Text(
                    text = stringResource(R.string.appointments_more, allAppointments.size - 3),
                    fontSize = 12.sp,
                    color = BrandTeal,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onViewAll)
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AppointmentRow(appointment: Appointment) {
    val scheduledInstant = Instant.ofEpochMilli(appointment.scheduledAt)
    val zoned = scheduledInstant.atZone(ZoneId.of("Africa/Nairobi"))
    val dateStr = zoned.format(appointmentDateFormatter)
    val timeStr = zoned.format(appointmentTimeFormatter)

    val statusColor = when (appointment.status.name.lowercase()) {
        "booked" -> Color(0xFF3B82F6)
        "confirmed" -> BrandTeal
        "in_progress" -> Color(0xFFF59E0B)
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appointment.serviceType.replaceFirstChar { it.uppercase() }.replace("_", " "),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.appointments_date_at_time, dateStr, timeStr),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (appointment.chiefComplaint.isNotBlank()) {
                Text(
                    text = appointment.chiefComplaint,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(statusColor.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = appointment.status.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor,
            )
        }
    }
}

// ─── Consultations Content ──────────────────────────────────────────────────────

private enum class ConsultationTab(val labelRes: Int) {
    PENDING(R.string.consultations_tab_pending),
    ACTIVE(R.string.consultations_tab_active),
    COMPLETED(R.string.consultations_tab_completed),
    CANCELLED(R.string.consultations_tab_cancelled),
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
                    contentDescription = stringResource(R.string.common_open_menu),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.consultations_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.consultations_subtitle),
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
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(20.dp))
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(tab.labelRes),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
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
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.consultations_empty, stringResource(selectedTab.labelRes).lowercase()),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
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
    onClick: (() -> Unit)? = null,
) {
    val statusColor = when (consultation.status) {
        "PENDING" -> Color(0xFFF59E0B)
        "ACTIVE" -> BrandTeal
        "COMPLETED" -> Color(0xFF22C55E)
        "CANCELLED" -> Color(0xFFDC2626)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
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
                color = MaterialTheme.colorScheme.onSurface,
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
            text = stringResource(R.string.consultation_fee, consultation.consultationFee),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.consultation_duration, consultation.sessionDurationMinutes),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
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
                    contentDescription = stringResource(R.string.common_open_menu),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.availability_settings_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.availability_settings_subtitle),
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
                    text = if (uiState.availabilitySaved) stringResource(R.string.availability_save_saved) else stringResource(R.string.availability_save_changes),
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
                text = stringResource(R.string.availability_quick_presets),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                PresetChip(stringResource(R.string.availability_preset_mon_fri)) { viewModel.applyPresetMonFri() }
                PresetChip(stringResource(R.string.availability_preset_every_day)) { viewModel.applyPresetEveryDay() }
                PresetChip(stringResource(R.string.availability_preset_clear_all)) { viewModel.clearAllAvailability() }
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
                    text = stringResource(R.string.availability_weekly_schedule),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Day name
        Text(
            text = dayName.take(3),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(36.dp),
        )

        // Toggle
        Switch(
            checked = schedule.enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = BrandTeal,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
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
                color = MaterialTheme.colorScheme.onSurface,
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
                text = stringResource(R.string.common_off),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
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
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = value,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
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
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = BrandTeal,
        focusedLabelColor = BrandTeal,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledBorderColor = MaterialTheme.colorScheme.outline,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                    contentDescription = stringResource(R.string.common_open_menu),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.profile_subtitle),
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
                    Text(stringResource(R.string.profile_photo_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                                contentDescription = stringResource(R.string.profile_photo_content_description),
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
                        Text(stringResource(R.string.profile_photo_tap_to_change), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BrandTeal)
                        Text(stringResource(R.string.profile_photo_size_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(stringResource(R.string.profile_personal_info), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = stringResource(R.string.profile_set_during_registration),
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
                        label = { Text(stringResource(R.string.profile_full_name)) },
                        enabled = false,
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.profilePhone,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.profile_phone_number)) },
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
                    label = { Text(stringResource(R.string.profile_email)) },
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
                    Text(stringResource(R.string.profile_professional_info), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = stringResource(R.string.profile_set_during_registration),
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
                        label = { Text(stringResource(R.string.profile_specialty)) },
                        enabled = false,
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.profileLicenseNumber,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.profile_license_number)) },
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
                    label = { Text(stringResource(R.string.profile_years_experience)) },
                    enabled = false,
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(0.48f),
                )
                Text(
                    text = stringResource(R.string.profile_years_experience_auto),
                    fontSize = 11.sp,
                    color = BrandTeal,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Professional Bio (read-only)
                Text(stringResource(R.string.profile_bio), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
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
                    Text(stringResource(R.string.profile_location_languages), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Country (read-only)
                OutlinedTextField(
                    value = uiState.profileCountry,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.profile_country)) },
                    enabled = false,
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(0.48f),
                )
                Text(
                    text = stringResource(R.string.profile_set_during_registration),
                    fontSize = 11.sp,
                    color = BrandTeal,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Languages (editable)
                Text(stringResource(R.string.profile_languages_title), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))

                Text(stringResource(R.string.profile_common_languages), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                Text(stringResource(R.string.profile_other_languages), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    text = stringResource(R.string.profile_languages_selected, uiState.profileLanguages.size),
                    fontSize = 11.sp,
                    color = BrandTeal,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Services Offered ──────────────────────────────────────
            ProfileSectionCard {
                Text(stringResource(R.string.profile_services_offered), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                        Text(stringResource(R.string.profile_available_for_consultations), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            stringResource(R.string.profile_available_toggle_hint),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiState.profileAvailableForConsultations,
                        onCheckedChange = { viewModel.onProfileAvailableToggled() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = BrandTeal,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline,
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
                            uiState.profileSaved && uiState.profileSyncFailed -> stringResource(R.string.profile_saved_locally)
                            uiState.profileSaved -> stringResource(R.string.profile_saved)
                            else -> stringResource(R.string.availability_save_changes)
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
                    text = stringResource(R.string.profile_sync_failed_hint),
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
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
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, BrandTeal, RoundedCornerShape(20.dp))
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
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
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
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
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_globe),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
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
    onNavigateToConsultation: (String) -> Unit,
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
                    contentDescription = stringResource(R.string.common_open_menu),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.chat_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.chat_subtitle),
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
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.chat_empty),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.chat_empty_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                uiState.activeConsultationsList.forEach { consultation ->
                    ConsultationCard(
                        consultation = consultation,
                        onClick = { onNavigateToConsultation(consultation.consultationId) },
                    )
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
                    contentDescription = stringResource(R.string.common_open_menu),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = stringResource(R.string.earnings_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.earnings_subtitle),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    label = stringResource(R.string.earnings_total),
                    iconRes = R.drawable.ic_dashboard,
                    iconColor = Color(0xFF22C55E),
                    iconBg = Color(0xFFDCFCE7),
                    modifier = Modifier.weight(1f),
                )
                EarningsStatCard(
                    value = uiState.thisMonthEarnings,
                    label = stringResource(R.string.earnings_this_month),
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
                    label = stringResource(R.string.earnings_last_month),
                    iconRes = R.drawable.ic_trending_up,
                    iconColor = Color(0xFF3B82F6),
                    iconBg = Color(0xFFDBEAFE),
                    modifier = Modifier.weight(1f),
                )
                EarningsStatCard(
                    value = uiState.pendingPayout,
                    label = stringResource(R.string.earnings_pending_payout),
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
                text = stringResource(R.string.earnings_payout_info_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD97706),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.earnings_payout_info_message),
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
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp),
        ) {
            Text(
                text = stringResource(R.string.earnings_recent_transactions),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
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
                        text = stringResource(R.string.earnings_no_transactions),
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
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = tx.date,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = tx.amount,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
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
                    contentDescription = stringResource(R.string.common_open_menu),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.common_coming_soon),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
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

// ── Full-screen Ban Notice ──────────────────────────────────────────────────────

@Composable
private fun BanNoticeScreen(
    banReason: String?,
    bannedAt: String?,
    onSignOut: () -> Unit,
) {
    val appealDeadline = remember(bannedAt) {
        try {
            val banned = java.time.Instant.parse(bannedAt)
            val deadline = banned.plus(java.time.Duration.ofDays(7))
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(deadline)
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Red circle with X icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFFEE2E2), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u2716",
                    fontSize = 36.sp,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.ban_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFDC2626),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.ban_message),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            if (!banReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))

                // Reason box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ban_reason_label),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFDC2626),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = banReason,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Appeal info box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F9FF))
                    .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.ban_appeal_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0369A1),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (appealDeadline != null) {
                        stringResource(R.string.ban_appeal_message_with_date, appealDeadline)
                    } else {
                        stringResource(R.string.ban_appeal_message_no_date)
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ban_support_email),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0369A1),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Sign out button
            TextButton(onClick = onSignOut) {
                Text(
                    text = stringResource(R.string.common_sign_out),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

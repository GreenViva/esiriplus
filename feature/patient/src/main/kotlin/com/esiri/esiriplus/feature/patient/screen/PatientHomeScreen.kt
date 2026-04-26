package com.esiri.esiriplus.feature.patient.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.ui.LanguageSwitchButton
import com.esiri.esiriplus.core.ui.theme.Geist
import com.esiri.esiriplus.core.ui.theme.Hairline
import com.esiri.esiriplus.core.ui.theme.Ink
import com.esiri.esiriplus.core.ui.theme.InkSoft
import com.esiri.esiriplus.core.ui.theme.InstrumentSerif
import com.esiri.esiriplus.core.ui.theme.Muted
import com.esiri.esiriplus.core.ui.theme.TealBg
import com.esiri.esiriplus.core.ui.theme.TealDeep
import com.esiri.esiriplus.core.ui.theme.TealSoft
import com.esiri.esiriplus.core.ui.theme.pressableClick
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.PatientHomeViewModel

private val HeroGradientStart  = Color(0xFF2DBE9E)
private val HeroGradientEnd    = Color(0xFF14302A)
private val ActiveBannerBg     = Color(0xFFE8F6F1)
private val ActiveBannerBorder = Color(0xFFC9E6DC)
private val ActiveBannerAccent = Color(0xFF1E8E76)
private val PulseDot           = Color(0xFF2DBE9E)

private data class QuickCard(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val showBadge: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    onStartConsultation: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToConsultationHistory: () -> Unit,
    onNavigateToAppointments: () -> Unit,
    onNavigateToOngoingConsultations: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onResumeConsultation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Wrap reports navigation to clear the unread dot.
    val handleNavigateToReports = {
        viewModel.markAllReportsRead()
        onNavigateToReports()
    }

    // Request POST_NOTIFICATIONS on Android 13+. FCM works either way, just
    // no system tray without it.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* fire-and-forget */ }

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

    val pendingRating = uiState.pendingRatingConsultation
    var showPendingRating by remember(pendingRating?.consultationId) {
        mutableStateOf(pendingRating != null)
    }
    if (showPendingRating && pendingRating != null) {
        RatingBottomSheet(
            consultationId = pendingRating.consultationId,
            doctorId = pendingRating.doctorId,
            patientSessionId = pendingRating.patientSessionId,
            onDismiss = { showPendingRating = false; viewModel.dismissPendingRating() },
            onSubmitSuccess = { showPendingRating = false; viewModel.clearPendingRating() },
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            soundsEnabled = uiState.soundsEnabled,
            onToggleSounds = viewModel::toggleSounds,
            onLogout = {
                showSettingsSheet = false
                showLogoutDialog = true
            },
            onDismiss = { showSettingsSheet = false },
        )
    }

    val pullRefreshState = rememberPullToRefreshState()

    val quickCards = remember(uiState.hasUnreadReports) {
        listOf(
            QuickCard(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Past chats",
                subtitle = "View history",
                showBadge = false,
                onClick = onNavigateToConsultationHistory,
            ),
            QuickCard(
                icon = Icons.Outlined.Description,
                title = "Reports",
                subtitle = if (uiState.hasUnreadReports) "New report ready" else "Tap to view",
                showBadge = uiState.hasUnreadReports,
                onClick = handleNavigateToReports,
            ),
            QuickCard(
                icon = Icons.Outlined.CalendarMonth,
                title = "Appointments",
                subtitle = "View upcoming",
                showBadge = false,
                onClick = onNavigateToAppointments,
            ),
            QuickCard(
                icon = Icons.Outlined.FavoriteBorder,
                title = "My health",
                subtitle = "Edit profile",
                showBadge = false,
                onClick = onNavigateToProfile,
            ),
        )
    }

    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        containerColor = TealBg,
        topBar = {
            HomeTopBar(
                maskedId = uiState.maskedPatientId,
                onSettingsClick = { showSettingsSheet = true },
            )
        },
        bottomBar = {
            HelpFooter(
                onPhoneClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:+255663582994")),
                    )
                },
                onEmailClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@esiri.africa")),
                    )
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(2.dp))

                HomeHeroCard(onStartConsultation = { onStartConsultation("") })

                Spacer(Modifier.height(8.dp))

                PendingBadge(
                    count = uiState.ongoingConsultations.size,
                    onResume = onNavigateToOngoingConsultations,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "YOUR RECORDS",
                    fontFamily = Geist,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Muted,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                QuickCardGrid(quickCards)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    maskedId: String,
    onSettingsClick: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = TealBg),
        title = {
            Column {
                Text(
                    text = buildAnnotatedString {
                        append("Hi there ")
                        append("👋")
                    },
                    fontFamily = InstrumentSerif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = Ink,
                )
                Text(
                    text = buildAnnotatedString {
                        append("ID: ")
                        withStyle(
                            SpanStyle(color = TealDeep, fontWeight = FontWeight.SemiBold),
                        ) { append(maskedId.ifBlank { "—" }) }
                    },
                    fontFamily = Geist,
                    fontSize = 10.sp,
                    color = Muted,
                    letterSpacing = 0.3.sp,
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Hairline, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = InkSoft,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun HomeHeroCard(onStartConsultation: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(colors = listOf(HeroGradientStart, HeroGradientEnd)),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column {
            Text(
                text = "READY WHEN YOU ARE",
                fontFamily = Geist,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                color = Color.White.copy(alpha = 0.75f),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = buildAnnotatedString {
                    append("Talk to a doctor\n")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("now.") }
                },
                fontFamily = InstrumentSerif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 26.sp,
                color = Color.White,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = "Choose a service · pay · get connected. Usually under 5 minutes.",
                fontFamily = Geist,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 15.sp,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .pressableClick(onClick = onStartConsultation)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Start consultation",
                    fontFamily = Geist,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TealDeep,
                )
                Spacer(Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = TealDeep,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun PendingBadge(count: Int, onResume: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pending_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pending_pulse_scale",
    )
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pending_pulse_alpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, ActiveBannerBorder, RoundedCornerShape(10.dp))
            .background(ActiveBannerBg)
            .pressableClick(onClick = onResume)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(PulseDot.copy(alpha = alpha * 0.4f)),
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ActiveBannerAccent),
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Pending",
                    fontFamily = Geist,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
                Spacer(Modifier.width(5.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(ActiveBannerAccent)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = count.toString(),
                        fontFamily = Geist,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Text(
                text = when {
                    count <= 0 -> "Nothing waiting on you right now"
                    count == 1 -> "1 consultation needs follow-up"
                    else -> "$count consultations need follow-up"
                },
                fontFamily = Geist,
                fontSize = 9.sp,
                color = Muted,
                lineHeight = 12.sp,
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(if (count > 0) TealDeep else TealDeep.copy(alpha = 0.4f))
                .pressableClick(enabled = count > 0, onClick = onResume)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Resume",
                fontFamily = Geist,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun QuickCardGrid(cards: List<QuickCard>) {
    val rows = cards.chunked(2)
    rows.forEachIndexed { index, row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { card ->
                QuickCardItem(
                    card = card,
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        if (index < rows.lastIndex) Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickCardItem(card: QuickCard, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White)
            .border(1.dp, Hairline, RoundedCornerShape(11.dp))
            .pressableClick(onClick = card.onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.TopEnd,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(TealSoft)
                    .align(Alignment.TopStart),
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = TealDeep,
                    modifier = Modifier.size(13.dp),
                )
            }
            if (card.showBadge) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.Red),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = card.title,
            fontFamily = Geist,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = card.subtitle,
            fontFamily = Geist,
            fontSize = 9.sp,
            color = Muted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    soundsEnabled: Boolean,
    onToggleSounds: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Settings",
                fontFamily = InstrumentSerif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                color = Ink,
            )
            Spacer(Modifier.height(16.dp))

            // Sounds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                    .pressableClick(onClick = onToggleSounds)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TealSoft),
                ) {
                    Icon(
                        imageVector = if (soundsEnabled) Icons.Outlined.VolumeUp
                                      else Icons.Outlined.VolumeOff,
                        contentDescription = null,
                        tint = TealDeep,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sounds",
                        fontFamily = Geist,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                    )
                    Text(
                        text = if (soundsEnabled) "Notification sounds on" else "Silent mode",
                        fontFamily = Geist,
                        fontSize = 11.sp,
                        color = Muted,
                    )
                }
                Switch(
                    checked = soundsEnabled,
                    onCheckedChange = { onToggleSounds() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TealDeep,
                    ),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Language
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                LanguageSwitchButton()
            }

            Spacer(Modifier.height(10.dp))

            // Logout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                    .pressableClick(onClick = onLogout)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFCE7E9)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Logout,
                        contentDescription = null,
                        tint = Color(0xFFC84856),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Log out",
                    fontFamily = Geist,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFC84856),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFC84856),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HelpFooter(
    onPhoneClick: () -> Unit,
    onEmailClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TealBg)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Need help? ",
                fontFamily = Geist,
                fontSize = 11.sp,
                color = Muted,
            )
            Text(
                text = "+255 663 582 994",
                fontFamily = Geist,
                fontSize = 11.sp,
                color = TealDeep,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onPhoneClick),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "info@esiri.africa",
            fontFamily = Geist,
            fontSize = 11.sp,
            color = TealDeep,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onEmailClick),
        )
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
                text = stringResource(R.string.home_logout_title),
                fontFamily = Geist,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.home_logout_message),
                fontFamily = Geist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.home_logout_confirm),
                    fontFamily = Geist,
                    color = Color.Red,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.home_logout_cancel),
                    fontFamily = Geist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

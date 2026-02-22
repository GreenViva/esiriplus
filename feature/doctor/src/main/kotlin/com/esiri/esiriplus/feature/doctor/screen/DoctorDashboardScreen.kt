package com.esiri.esiriplus.feature.doctor.screen

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorDashboardUiState
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorDashboardViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val DarkText = Color.Black
private val CardBorder = Color(0xFFE5E7EB)
private val VerifiedBg = Color(0xFFECFDF5)
private val VerifiedBorder = Color(0xFFBBF7D0)
private val SectionBg = Color(0xFFF9FAFB)

private val TabLabels = listOf("All Consultations", "Availability", "Earnings", "Profile")

@Composable
fun DoctorDashboardScreen(
    onNavigateToConsultations: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandTeal)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FFFE))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar
        TopBar(
            doctorName = uiState.doctorName,
            isOnline = uiState.isOnline,
            onToggleOnline = viewModel::onToggleOnline,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Verified banner
        if (uiState.isVerified) {
            VerifiedBanner(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tab row
        TabChipRow(modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(modifier = Modifier.height(16.dp))

        // Stats cards
        StatsRow(uiState = uiState)

        Spacer(modifier = Modifier.height(20.dp))

        // Today's Availability
        AvailabilitySection(
            isAvailable = uiState.isAvailable,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pending Requests
        PendingRequestsSection(
            count = uiState.pendingRequests,
            onViewAll = onNavigateToConsultations,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Sign Out
        TextButton(
            onClick = {
                viewModel.onSignOut()
                onSignOut()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = BrandTeal,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sign Out",
                color = BrandTeal,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TopBar(
    doctorName: String,
    isOnline: Boolean,
    onToggleOnline: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Welcome back,",
                fontSize = 14.sp,
                color = DarkText,
            )
            Text(
                text = doctorName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )
        }

        // Online indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF22C55E), CircleShape),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "Online",
                fontSize = 13.sp,
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
    }
}

@Composable
private fun VerifiedBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, VerifiedBorder, RoundedCornerShape(12.dp))
            .background(VerifiedBg)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(BrandTeal, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "Verified Doctor",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Your profile is verified. You can now accept patient consultations.",
                fontSize = 14.sp,
                color = DarkText,
                lineHeight = 20.sp,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TabLabels.forEachIndexed { index, label ->
            val isSelected = index == selectedTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(BrandTeal)
                        } else {
                            Modifier.border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                        },
                    )
                    .clickable { selectedTab = index }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) Color.White else DarkText,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(uiState: DoctorDashboardUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            value = "${uiState.pendingRequests}",
            label = "Pending\nRequests",
            iconColor = Color(0xFFF59E0B),
            iconBg = Color(0xFFFEF3C7),
        )
        StatCard(
            value = "${uiState.activeConsultations}",
            label = "Active\nConsultations",
            iconColor = BrandTeal,
            iconBg = Color(0xFFE0F2FE),
        )
        StatCard(
            value = uiState.todaysEarnings,
            label = "Today's\nEarnings",
            iconColor = Color(0xFF22C55E),
            iconBg = Color(0xFFDCFCE7),
        )
        StatCard(
            value = "${uiState.totalPatients}",
            label = "Total\nPatients",
            iconColor = Color(0xFF3B82F6),
            iconBg = Color(0xFFDBEAFE),
        )
        StatCard(
            value = uiState.acceptanceRate,
            label = "Acceptance\nRate",
            iconColor = BrandTeal,
            iconBg = Color(0xFFD1FAE5),
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    iconColor: Color,
    iconBg: Color,
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(iconColor, CircleShape),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = DarkText,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun AvailabilitySection(
    isAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = DarkText,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Today's Availability",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = CardBorder,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isAvailable) "Available today" else "Not available today",
                fontSize = 14.sp,
                color = DarkText,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Set Availability",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkText,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
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
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Pending Requests",
                fontSize = 16.sp,
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
                    fontSize = 13.sp,
                    color = DarkText,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = DarkText,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = CardBorder,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (count == 0) "No pending requests" else "$count pending request(s)",
                fontSize = 14.sp,
                color = DarkText,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

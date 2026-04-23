package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.AgentDashboardViewModel
import java.text.NumberFormat
import java.util.Locale

private val AgentAmber = Color(0xFFF59E0B)
private val AgentOrange = Color(0xFFEF6C00)
private val BrandTeal = Color(0xFF2A9D8F)
private val WarmBackground = Color(0xFFFFF7ED)
private val CardBackground = Color(0xFFFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDashboardScreen(
    onStartConsultation: () -> Unit,
    onSignedOut: () -> Unit,
    onNavigateToEarnings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgentDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) {
            onSignedOut()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.sessionReady.collect {
            onStartConsultation()
        }
    }

    // Re-pull earnings badge every time the dashboard is shown, so
    // admin-side "mark paid" is reflected without a sign-out.
    LaunchedEffect(Unit) {
        viewModel.refreshEarningsSummary()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Agent Dashboard",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WarmBackground,
                ),
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmBackground)
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Agent greeting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            Brush.linearGradient(listOf(AgentAmber, AgentOrange)),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.agentName.firstOrNull()?.uppercase() ?: "A",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Welcome back,",
                        fontSize = 14.sp,
                        color = Color.Black.copy(alpha = 0.7f),
                    )
                    Text(
                        text = uiState.agentName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.Black,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Error message
            uiState.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = Color.Red,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Start Consultation card
            AgentActionCard(
                icon = Icons.Default.PlayArrow,
                title = if (uiState.isCreatingSession) "Creating session..." else "Start Consultation",
                subtitle = "Help a patient start a new consultation",
                gradientColors = listOf(AgentAmber, AgentOrange),
                onClick = {
                    if (!uiState.isCreatingSession) {
                        viewModel.startConsultation()
                    }
                },
            )

            Spacer(Modifier.height(16.dp))

            // Earnings card with pending-count badge
            AgentActionCard(
                icon = Icons.Default.AccountBalanceWallet,
                title = "Earnings",
                subtitle = if (uiState.pendingEarningsCount > 0)
                    "TZS ${formatAmount(uiState.pendingEarningsAmount)} pending payment"
                else
                    "View your commission history",
                gradientColors = listOf(BrandTeal, Color(0xFF1F7A6F)),
                badge = uiState.pendingEarningsCount.takeIf { it > 0 },
                onClick = onNavigateToEarnings,
            )

            Spacer(Modifier.height(16.dp))

            // Finished Consultations card (placeholder)
            AgentActionCard(
                icon = Icons.Default.DateRange,
                title = "Finished Consultations",
                subtitle = "View completed consultation history",
                gradientColors = listOf(Color(0xFF6B7280), Color(0xFF4B5563)),
                onClick = { /* Placeholder - coming soon */ },
            )

            Spacer(Modifier.weight(1f))

            // Sign out button
            Button(
                onClick = { viewModel.signOut() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.1f),
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Sign Out",
                    color = Color.Red,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AgentActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    badge: Int? = null,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.linearGradient(gradientColors),
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.Black,
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEF4444), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = if (badge > 99) "99+" else badge.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.Black.copy(alpha = 0.7f),
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = gradientColors.first(),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun formatAmount(value: Long): String =
    NumberFormat.getNumberInstance(Locale.US).format(value)

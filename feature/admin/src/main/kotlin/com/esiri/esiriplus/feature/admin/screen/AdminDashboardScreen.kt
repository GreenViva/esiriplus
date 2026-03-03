package com.esiri.esiriplus.feature.admin.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.admin.viewmodel.AdminDashboardViewModel
import com.esiri.esiriplus.feature.admin.viewmodel.AdminPlatformStats
import com.esiri.esiriplus.feature.admin.viewmodel.AdminReportViewModel
import com.esiri.esiriplus.feature.admin.viewmodel.DoctorStats
import java.text.NumberFormat
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onManageDoctors: () -> Unit,
    onRatingsFeedback: () -> Unit,
    onAuditLog: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Report ViewModel
    val reportViewModel: AdminReportViewModel = hiltViewModel()
    val reportState by reportViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard", color = Color.Black, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    IconButton(onClick = { viewModel.loadStats() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = BrandTeal,
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandTeal)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Alert card for pending doctors
            if (uiState.stats.pending > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Pending Verifications",
                                color = Color.Black,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            Text(
                                "${uiState.stats.pending} doctor(s) awaiting verification",
                                color = Color.Black,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // Platform overview (revenue, patients, consultations)
            Text(
                "Platform Overview",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )

            PlatformOverviewGrid(uiState.platformStats)

            uiState.platformError?.let { platformError ->
                Text(platformError, color = Color(0xFFDC2626), fontSize = 13.sp)
            }

            // Stats grid
            Text(
                "Doctor Statistics",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )

            StatsGrid(uiState.stats)

            // Manage doctors button
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onManageDoctors,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Manage Doctors", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onRatingsFeedback,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Ratings & Feedback", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onAuditLog,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Audit Log", color = Color.White, fontWeight = FontWeight.SemiBold)
            }

            // Generate Analytics Report
            Button(
                onClick = { reportViewModel.generateAnalyticsReport() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(12.dp),
                enabled = !reportState.isGenerating,
            ) {
                if (reportState.isGenerating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Generating...", color = Color.White, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Generate AI Analytics Report", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            // Error
            uiState.error?.let { error ->
                Text(error, color = Color(0xFFDC2626), fontSize = 13.sp)
            }
        }
    }

    // Report dialog
    if (reportState.showReport) {
        ReportDialog(
            title = reportState.reportTitle,
            subtitle = reportState.reportSubtitle,
            sections = reportState.sections,
            onDismiss = { reportViewModel.dismissReport() },
        )
    }

    // Report error
    LaunchedEffect(reportState.error) {
        reportState.error?.let {
            snackbarHostState.showSnackbar(it)
            reportViewModel.dismissError()
        }
    }
}

@Composable
private fun StatsGrid(stats: DoctorStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("Total", stats.total, Color(0xFF6366F1), Modifier.weight(1f))
            StatCard("Pending", stats.pending, Color(0xFFD97706), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("Active", stats.active, BrandTeal, Modifier.weight(1f))
            StatCard("Suspended", stats.suspended, Color(0xFFEA580C), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("Rejected", stats.rejected, Color(0xFFDC2626), Modifier.weight(1f))
            StatCard("Banned", stats.banned, Color(0xFF7C2D12), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                count.toString(),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
            Text(label, color = Color.Black, fontSize = 13.sp)
        }
    }
}

private fun formatTZS(amount: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    return "TZS ${formatter.format(amount)}"
}

@Composable
private fun PlatformOverviewGrid(stats: AdminPlatformStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RevenueCard(
                label = "Total Revenue",
                amount = formatTZS(stats.revenue.total),
                subtitle = "${stats.revenue.completedPayments} payments",
                color = Color(0xFF059669),
                modifier = Modifier.weight(1f),
            )
            RevenueCard(
                label = "Platform Profit",
                amount = formatTZS(stats.revenue.platformCommission),
                subtitle = "50% commission",
                color = Color(0xFF2563EB),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RevenueCard(
                label = "Patients",
                amount = stats.patients.total.toString(),
                subtitle = "total registered",
                color = Color(0xFF7C3AED),
                modifier = Modifier.weight(1f),
            )
            RevenueCard(
                label = "Consultations",
                amount = stats.consultations.total.toString(),
                subtitle = "${stats.consultations.completed} completed",
                color = Color(0xFFDB2777),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RevenueCard(
                label = "Doctor Payouts",
                amount = formatTZS(stats.doctorEarnings.total),
                subtitle = "TZS ${NumberFormat.getNumberInstance(Locale.US).format(stats.doctorEarnings.unpaid)} unpaid",
                color = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
            )
            RevenueCard(
                label = "Doctor Paid",
                amount = formatTZS(stats.doctorEarnings.paid),
                subtitle = "already paid out",
                color = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
            )
        }
        if (stats.revenue.pending > 0) {
            RevenueCard(
                label = "Pending Revenue",
                amount = formatTZS(stats.revenue.pending),
                subtitle = "${stats.revenue.pendingPayments} pending payments",
                color = Color(0xFFD97706),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RevenueCard(
    label: String,
    amount: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                amount,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
            )
            Text(label, color = Color.Black, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(subtitle, color = Color.Black, fontSize = 11.sp)
        }
    }
}

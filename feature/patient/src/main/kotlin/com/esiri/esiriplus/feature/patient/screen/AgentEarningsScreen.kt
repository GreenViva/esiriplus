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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.network.api.model.AgentEarningApiModel
import com.esiri.esiriplus.feature.patient.viewmodel.AgentEarningsViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AgentAmber = Color(0xFFF59E0B)
private val AgentOrange = Color(0xFFEF6C00)
private val BrandTeal = Color(0xFF2A9D8F)
private val WarmBackground = Color(0xFFFFF7ED)
private val CardBackground = Color(0xFFFFFFFF)
private val PendingAmber = Color(0xFFFFF7ED)
private val PendingAmberText = Color(0xFFB45309)
private val PaidGreenBg = Color(0xFFE8F6F4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEarningsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgentEarningsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Paginate when the user scrolls near the bottom.
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layout.totalItemsCount
            total > 0 && last >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingPage) {
            viewModel.loadNextPage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Earnings", fontWeight = FontWeight.Bold, color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmBackground),
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmBackground)
                .padding(padding),
        ) {
            // Summary strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.HourglassTop,
                    label = "Pending",
                    amount = state.summary.pendingAmount,
                    countHint = "${state.summary.pendingCount} entries",
                    gradient = listOf(AgentAmber, AgentOrange),
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle,
                    label = "Paid",
                    amount = state.summary.paidAmount,
                    countHint = "Lifetime",
                    gradient = listOf(BrandTeal, Color(0xFF1F7A6F)),
                )
            }

            // Header for the list
            Text(
                text = "Recent earnings",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            when {
                state.isLoading && state.rows.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandTeal)
                    }
                }
                state.rows.isEmpty() -> EmptyEarnings(state.errorMessage)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        EarningRow(row = row)
                    }
                    if (state.isLoadingPage) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = BrandTeal,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    } else if (!state.hasMore && state.rows.isNotEmpty()) {
                        item {
                            Text(
                                text = "Showing all ${state.totalCount} earnings",
                                fontSize = 12.sp,
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 20.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    icon: ImageVector,
    label: String,
    amount: Long,
    countHint: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Brush.linearGradient(gradient), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(label, fontSize = 13.sp, color = Color.Black.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "TZS ${formatAmount(amount)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color.Black,
            )
            Spacer(Modifier.height(2.dp))
            Text(countHint, fontSize = 11.sp, color = Color.Black.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun EarningRow(row: AgentEarningApiModel) {
    val pending = row.status == "pending"
    val statusBg = if (pending) PendingAmber else PaidGreenBg
    val statusFg = if (pending) PendingAmberText else BrandTeal
    val statusLabel = if (pending) "Pending" else row.status.replaceFirstChar { it.uppercase() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = AgentOrange,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "TZS ${formatAmount(row.amount.toLong())}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatDate(row.createdAt)} · Consultation ${row.consultationId.take(8)}",
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.6f),
                )
            }
            Box(
                modifier = Modifier
                    .background(statusBg, RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = statusLabel,
                    color = statusFg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyEarnings(errorMessage: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.AccountBalanceWallet,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = errorMessage ?: "No earnings yet",
            color = Color.Black,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (errorMessage == null)
                "Earnings appear here after your referred consultations complete."
            else
                "Pull to refresh",
            color = Color.Black.copy(alpha = 0.55f),
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun formatAmount(value: Long): String =
    NumberFormat.getNumberInstance(Locale.US).format(value)

private fun formatDate(iso: String): String = try {
    val instant = Instant.parse(iso)
    DateTimeFormatter
        .ofPattern("dd MMM yyyy", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
} catch (_: Exception) {
    iso.take(10)
}

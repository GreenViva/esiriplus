package com.esiri.esiriplus.feature.doctor.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.doctor.R
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorUnsubmittedReportsViewModel
import com.esiri.esiriplus.feature.doctor.viewmodel.UnsubmittedReportItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val AmberAccent = Color(0xFFEA580C)
private val AmberBg = Color(0xFFFFF7ED)
private val CardBorder = Color(0xFFE5E7EB)
private val ScreenBg = Color(0xFFF8FFFE)

private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm", Locale.ENGLISH)
private val EAT = ZoneId.of("Africa/Nairobi")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorUnsubmittedReportsScreen(
    onReportSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorUnsubmittedReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        Header(onBack = onBack)

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.items.isEmpty() -> EmptyState()
                else -> ReportList(items = uiState.items, onItemClick = onReportSelected)
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back_content_description),
                tint = Color.Black,
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.unsubmitted_reports_title),
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandTeal)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = BrandTeal,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.unsubmitted_reports_empty_title),
            color = Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.unsubmitted_reports_empty_body),
            color = Color.Black,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ReportList(
    items: List<UnsubmittedReportItem>,
    onItemClick: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.unsubmitted_reports_hint),
                color = Color.Black,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        items(items, key = { it.consultationId }) { item ->
            ReportRow(item = item, onClick = { onItemClick(item.consultationId) })
        }
    }
}

@Composable
private fun ReportRow(item: UnsubmittedReportItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AmberBg)
            .border(1.dp, AmberAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.unsubmitted_reports_row_patient,
                    item.patientId.ifBlank { "—" },
                ),
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.chiefComplaint.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.chiefComplaint,
                    color = Color.Black,
                    fontSize = 13.sp,
                    maxLines = 2,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatEnded(item.endedAtMillis),
                color = Color.Black,
                fontSize = 12.sp,
            )
        }
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(R.string.unsubmitted_reports_file_cta),
            tint = AmberAccent,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun formatEnded(millis: Long): String {
    return try {
        val zdt = Instant.ofEpochMilli(millis).atZone(EAT)
        dateTimeFormatter.format(zdt)
    } catch (_: Exception) {
        ""
    }
}

package com.esiri.esiriplus.feature.doctor.screen

import com.esiri.esiriplus.core.ui.EmptyState
import com.esiri.esiriplus.core.ui.LoadingScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.doctor.R
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorConsultationListViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF374151)
private val SuccessGreen = Color(0xFF16A34A)
private val WarningOrange = Color(0xFFEA580C)

private val EAT = ZoneId.of("Africa/Nairobi")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' HH:mm", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorConsultationListScreen(
    onConsultationSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorConsultationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FFFE)),
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onBack),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back_content_description),
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = stringResource(R.string.common_back), fontSize = 14.sp, color = BrandTeal)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.consultation_list_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        val pullRefreshState = rememberPullToRefreshState()

        if (uiState.isLoading) {
            LoadingScreen()
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullRefreshState,
                modifier = Modifier.weight(1f),
            ) {
                if (uiState.consultations.isEmpty()) {
                    EmptyState(title = stringResource(R.string.consultation_list_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.consultations, key = { it.consultationId }) { consultation ->
                            ConsultationHistoryCard(
                                consultation = consultation,
                                onClick = { onConsultationSelected(consultation.consultationId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsultationHistoryCard(
    consultation: ConsultationEntity,
    onClick: () -> Unit,
) {
    val statusColor = when (consultation.status.lowercase()) {
        "active", "awaiting_extension", "grace_period" -> BrandTeal
        "completed" -> SuccessGreen
        else -> WarningOrange
    }
    val statusLabel = consultation.status.lowercase()
        .replaceFirstChar { it.uppercase() }
        .replace("_", " ")

    val dateText = consultation.sessionStartTime?.let {
        Instant.ofEpochMilli(it).atZone(EAT).format(dateTimeFormatter)
    } ?: Instant.ofEpochMilli(consultation.createdAt).atZone(EAT).format(dateTimeFormatter)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = consultation.serviceType.replaceFirstChar { it.uppercase() }.replace("_", " "),
                        fontSize = 13.sp,
                        color = SubtitleGrey,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                    )
                }
            }
        }
    }
}

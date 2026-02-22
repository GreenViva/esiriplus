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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.PatientReport
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.ReportsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF1F2937)

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color.White, MintLight))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            // Top bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Reports",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            }

            Spacer(Modifier.height(16.dp))

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = BrandTeal)
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = uiState.error ?: "Unknown error",
                            color = Color.Red,
                            fontSize = 14.sp,
                        )
                    }
                }
                uiState.reports.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_reports),
                                contentDescription = null,
                                tint = CardBorder,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No reports yet",
                                color = SubtitleGrey,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.reports, key = { it.reportId }) { report ->
                            ReportItem(report)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportItem(report: PatientReport) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Consultation: ${report.consultationId.take(8)}...",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(report.generatedAt),
                    fontSize = 13.sp,
                    color = SubtitleGrey,
                )
            }
            DownloadStatusChip(report.isDownloaded)
        }
    }
}

@Composable
private fun DownloadStatusChip(isDownloaded: Boolean) {
    val (bgColor, textColor, label) = if (isDownloaded) {
        Triple(Color(0xFFD1FAE5), Color(0xFF065F46), "Downloaded")
    } else {
        Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "Pending")
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

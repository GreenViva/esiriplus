package com.esiri.esiriplus.feature.doctor.screen

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorDashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RoyalPurple = Color(0xFF4C1D95)
private val RoyalGold = Color(0xFFF59E0B)
private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun RoyalClientsScreen(
    onBack: () -> Unit,
    onOpenConsultation: (consultationId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DoctorDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val royalConsultations = uiState.royalConsultations

    Surface(modifier = modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(RoyalPurple, Color(0xFF7C3AED))),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Royal Clients",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "${royalConsultations.size} consultation${if (royalConsultations.size != 1) "s" else ""}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                }
            }

            if (royalConsultations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "\u2605",
                            fontSize = 48.sp,
                            color = RoyalGold.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No Royal consultations yet",
                            color = Color.Black,
                            fontSize = 16.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(royalConsultations, key = { it.consultationId }) { consultation ->
                        RoyalClientCard(
                            consultation = consultation,
                            onClick = { onOpenConsultation(consultation.consultationId) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RoyalClientCard(
    consultation: ConsultationEntity,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()) }
    val feeFormatted = remember(consultation.consultationFee) {
        "TSh ${"%,d".format(consultation.consultationFee)}"
    }

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Purple header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(RoyalPurple, Color(0xFF7C3AED))),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "\u2605 Royal",
                        color = RoyalGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = consultation.status.lowercase().replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }

            // Body
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when (consultation.status.lowercase()) {
                                "active", "in_progress" -> BrandTeal
                                "completed" -> Color(0xFF10B981)
                                else -> RoyalGold
                            },
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Service type
                    Text(
                        text = consultation.serviceType.replace("_", " ")
                            .lowercase().replaceFirstChar { it.uppercase() },
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Date
                    Text(
                        text = dateFormat.format(Date(consultation.createdAt)),
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Fee
                    Text(
                        text = feeFormatted,
                        color = RoyalPurple,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

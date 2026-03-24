package com.esiri.esiriplus.feature.patient.screen

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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.OngoingConsultationItem
import com.esiri.esiriplus.feature.patient.viewmodel.OngoingConsultationsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val BrandTeal = Color(0xFF2A9D8F)
private val RoyalPurple = Color(0xFF4C1D95)
private val RoyalGold = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OngoingConsultationsScreen(
    onBack: () -> Unit,
    onOpenConsultation: (consultationId: String) -> Unit,
    onRequestFollowUp: (parentConsultationId: String, doctorId: String, serviceType: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: OngoingConsultationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    // Follow-up confirmation dialog state
    var followUpItem by remember { mutableStateOf<OngoingConsultationItem?>(null) }

    // Follow-up confirmation dialog
    followUpItem?.let { item ->
        AlertDialog(
            onDismissRequest = { followUpItem = null },
            title = {
                Text(
                    text = stringResource(R.string.followup_confirm_title),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Request a follow-up consultation with Dr. ${item.doctorName}?",
                    color = Color.Black,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val c = item.consultation
                        followUpItem = null
                        onRequestFollowUp(c.consultationId, c.doctorId, c.serviceType)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.followup_confirm_yes),
                        color = BrandTeal,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { followUpItem = null }) {
                    Text(
                        text = stringResource(R.string.followup_confirm_no),
                        color = Color.Black,
                    )
                }
            },
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1A7A6E), BrandTeal),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.tier_back),
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.home_ongoing_consultations),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = BrandTeal)
                    }
                } else if (uiState.consultations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Chat,
                                contentDescription = null,
                                tint = BrandTeal.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.ongoing_consultations_empty),
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
                        items(uiState.consultations, key = { it.consultation.consultationId }) { item ->
                            val consultation = item.consultation
                            val now = System.currentTimeMillis()
                            val isFollowUpEligible =
                                consultation.status.lowercase() == "completed" &&
                                (consultation.followUpExpiry ?: 0L) > now

                            OngoingConsultationCard(
                                consultation = consultation,
                                doctorName = item.doctorName,
                                onClick = {
                                    if (isFollowUpEligible) {
                                        followUpItem = item
                                    } else {
                                        onOpenConsultation(consultation.consultationId)
                                    }
                                },
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OngoingConsultationCard(
    consultation: ConsultationEntity,
    doctorName: String,
    onClick: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val isFollowUp = consultation.status.lowercase() == "completed" &&
        (consultation.followUpExpiry ?: 0L) > now
    val isRoyal = consultation.serviceTier.uppercase() == "ROYAL"

    val followUpExpiry = consultation.followUpExpiry
    val daysRemaining = if (isFollowUp && followUpExpiry != null) {
        val millis = followUpExpiry - now
        TimeUnit.MILLISECONDS.toDays(millis).toInt().coerceAtLeast(0)
    } else null

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Follow-up header band
            if (isFollowUp) {
                val bandColors = if (isRoyal) {
                    listOf(RoyalPurple, Color(0xFF7C3AED))
                } else {
                    listOf(Color(0xFF0D6EFD), Color(0xFF3B82F6))
                }
                val labelColor = if (isRoyal) RoyalGold else Color.White
                val label = if (isRoyal) {
                    stringResource(R.string.ongoing_follow_up_mode)
                } else {
                    "Follow-up (1 free)"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(bandColors),
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
                            text = label,
                            color = labelColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        if (daysRemaining != null) {
                            Text(
                                text = stringResource(R.string.ongoing_follow_up_days_remaining, daysRemaining),
                                color = Color.White,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // Card body
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
                            when {
                                isFollowUp && isRoyal -> RoyalGold
                                isFollowUp -> Color(0xFF0D6EFD)
                                consultation.status.lowercase() in listOf("active", "in_progress") -> BrandTeal
                                else -> Color(0xFFF59E0B)
                            },
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Doctor name
                    Text(
                        text = "Dr. $doctorName",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Service type
                    Text(
                        text = consultation.serviceType.replace("_", " ")
                            .lowercase().replaceFirstChar { it.uppercase() },
                        color = Color.Black,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Date and time
                    Text(
                        text = dateFormat.format(Date(consultation.createdAt)),
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Status
                    Text(
                        text = statusLabel(consultation.status, isFollowUp, isRoyal),
                        color = when {
                            isFollowUp && isRoyal -> RoyalPurple
                            isFollowUp -> Color(0xFF0D6EFD)
                            else -> BrandTeal
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                // Tier badge
                if (isRoyal) {
                    Text(
                        text = "\u2605 Royal",
                        color = RoyalGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(
                                RoyalPurple.copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                } else if (isFollowUp) {
                    Text(
                        text = "Economy",
                        color = Color(0xFF0D6EFD),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(
                                Color(0xFF0D6EFD).copy(alpha = 0.1f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: String, isFollowUp: Boolean, isRoyal: Boolean = false): String {
    if (isFollowUp && isRoyal) return "Follow-up window open (unlimited)"
    if (isFollowUp) return "Follow-up window open (1 free)"
    return when (status.lowercase()) {
        "active" -> "In consultation"
        "in_progress" -> "In consultation"
        "awaiting_extension" -> "Awaiting extension"
        "grace_period" -> "Grace period"
        "completed" -> "Completed"
        else -> status.lowercase().replaceFirstChar { it.uppercase() }
    }
}

package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.esiri.esiriplus.core.ui.theme.Hairline
import com.esiri.esiriplus.core.ui.theme.Ink
import com.esiri.esiriplus.core.ui.theme.Muted
import com.esiri.esiriplus.core.ui.theme.TealBg
import com.esiri.esiriplus.core.ui.theme.TealDeep
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.MissedConsultationItem
import com.esiri.esiriplus.feature.patient.viewmodel.MissedConsultationsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MissedRed = Color(0xFFDC2626)
private val MissedRedSoft = Color(0xFFFEE2E2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedConsultationsScreen(
    onBack: () -> Unit,
    onReconnect: (item: MissedConsultationItem) -> Unit,
    viewModel: MissedConsultationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = TealBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealBg),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Ink,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.missed_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                    )
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = TealDeep) }

            state.items.isEmpty() -> EmptyMissed(modifier = Modifier.fillMaxSize().padding(padding))

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    text = stringResource(R.string.missed_subtitle),
                    fontSize = 13.sp,
                    color = Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { "${it.sourceKind}:${it.sourceId}" }) { item ->
                        MissedRow(item = item, onReconnect = { onReconnect(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMissed(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MissedRedSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = MissedRed,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.missed_empty_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.missed_empty_body),
                fontSize = 13.sp,
                color = Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MissedRow(
    item: MissedConsultationItem,
    onReconnect: () -> Unit,
) {
    val service = localizedCategoryDisplayName(item.serviceType)
    val tierLabel = stringResource(
        if (item.serviceTier.equals("ROYAL", ignoreCase = true)) {
            R.string.services_tier_label_royal
        } else {
            R.string.services_tier_label_economy
        },
    )
    val reasonLabel = stringResource(
        when (item.sourceKind) {
            "request_expired" -> R.string.missed_reason_request_expired
            "no_engagement" -> R.string.missed_reason_no_engagement
            "paid_no_request" -> R.string.missed_reason_paid_no_request
            else -> R.string.missed_reason_request_expired
        },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MissedRedSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = MissedRed,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "$tierLabel · ${formatRelative(item.createdAtIso)}",
                    fontSize = 11.sp,
                    color = Muted,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = reasonLabel,
            fontSize = 12.sp,
            color = MissedRed,
            fontWeight = FontWeight.Medium,
        )

        if (!item.doctorName.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.missed_with_doctor, item.doctorName),
                fontSize = 11.sp,
                color = Muted,
            )
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onReconnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TealDeep,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.missed_reconnect_cta),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun localizedCategoryDisplayName(code: String): String {
    return when (code.lowercase()) {
        "nurse" -> stringResource(R.string.category_nurse)
        "clinical_officer" -> stringResource(R.string.category_clinical_officer)
        "pharmacist" -> stringResource(R.string.category_pharmacist)
        "gp" -> stringResource(R.string.category_gp)
        "specialist" -> stringResource(R.string.category_specialist)
        "psychologist" -> stringResource(R.string.category_psychologist)
        "herbalist" -> stringResource(R.string.category_herbalist)
        "drug_interaction" -> stringResource(R.string.category_drug_interaction)
        else -> code
    }
}

private val DateFormatter = DateTimeFormatter
    .ofPattern("MMM d · HH:mm", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private fun formatRelative(iso: String): String {
    return try {
        DateFormatter.format(Instant.parse(iso))
    } catch (_: Exception) {
        iso
    }
}

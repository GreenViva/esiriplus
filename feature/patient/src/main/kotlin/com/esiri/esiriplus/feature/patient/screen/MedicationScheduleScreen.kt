package com.esiri.esiriplus.feature.patient.screen

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.MedicationScheduleViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.MedicationTimetableDto

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF374151)
private val SuccessGreen = Color(0xFF10B981)

@Composable
fun MedicationScheduleScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicationScheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color.White, MintLight))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onBack),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = BrandTeal,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = stringResource(R.string.appointments_back), fontSize = 14.sp, color = BrandTeal)
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.med_schedule_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.med_schedule_subtitle),
                    fontSize = 14.sp,
                    color = SubtitleGrey,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            if (uiState.isLoading) {
                com.esiri.esiriplus.core.ui.LoadingScreen()
            } else if (uiState.timetables.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = BrandTeal.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.med_schedule_empty_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.med_schedule_empty_body),
                            fontSize = 14.sp,
                            color = SubtitleGrey,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.timetables, key = { it.timetableId }) { timetable ->
                        MedicationCard(timetable)
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MedicationCard(timetable: MedicationTimetableDto) {
    Surface(
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
                        text = timetable.medicationName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    if (!timetable.dosage.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = timetable.dosage,
                            fontSize = 13.sp,
                            color = SubtitleGrey,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (timetable.isActive) SuccessGreen.copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f),
                ) {
                    Text(
                        text = if (timetable.isActive) stringResource(R.string.med_schedule_active) else stringResource(R.string.med_schedule_ended),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (timetable.isActive) SuccessGreen else Color(0xFFEF4444),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Reminder times
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                timetable.scheduledTimes.forEach { time ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BrandTeal.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = time,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTeal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Duration info
            Text(
                text = stringResource(R.string.med_schedule_date_range, timetable.startDate, timetable.endDate, timetable.durationDays),
                fontSize = 12.sp,
                color = SubtitleGrey,
            )
        }
    }
}

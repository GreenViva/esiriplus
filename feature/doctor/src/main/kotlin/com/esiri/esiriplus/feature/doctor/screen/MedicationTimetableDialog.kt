package com.esiri.esiriplus.feature.doctor.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.feature.doctor.R

private val BrandTeal = Color(0xFF2A9D8F)

/** Default time suggestions based on times-per-day selection. */
private val TIME_DEFAULTS = mapOf(
    1 to listOf("08:00"),
    2 to listOf("08:00", "20:00"),
    3 to listOf("08:00", "14:00", "20:00"),
    4 to listOf("06:00", "12:00", "18:00", "22:00"),
)

@Composable
fun MedicationTimetableDialog(
    medicationName: String,
    defaultDays: Int = 7,
    onConfirm: (timesPerDay: Int, scheduledTimes: List<String>, durationDays: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var timesPerDay by remember { mutableIntStateOf(3) }
    val scheduledTimes = remember { mutableStateListOf(*TIME_DEFAULTS[3]!!.toTypedArray()) }
    var durationDays by remember { mutableStateOf(defaultDays.toString()) }

    fun updateTimesPerDay(n: Int) {
        timesPerDay = n
        scheduledTimes.clear()
        scheduledTimes.addAll(TIME_DEFAULTS[n] ?: listOf("08:00"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.med_timetable_title),
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = medicationName,
                    color = BrandTeal,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Times per day selector
                Text(stringResource(R.string.med_timetable_times_per_day), fontWeight = FontWeight.SemiBold, color = Color.Black, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (n in 1..4) {
                        val selected = timesPerDay == n
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) BrandTeal else Color.White,
                            border = BorderStroke(1.dp, if (selected) BrandTeal else Color(0xFFE5E7EB)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { updateTimesPerDay(n) },
                        ) {
                            Text(
                                text = "${n}x",
                                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) Color.White else Color.Black,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Time slots
                Text(stringResource(R.string.med_timetable_reminder_times), fontWeight = FontWeight.SemiBold, color = Color.Black, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                for (i in scheduledTimes.indices) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = scheduledTimes[i],
                            onValueChange = { value ->
                                // Simple HH:MM validation
                                val filtered = value.filter { it.isDigit() || it == ':' }.take(5)
                                scheduledTimes[i] = filtered
                            },
                            label = { Text(stringResource(R.string.med_timetable_time_label, i + 1)) },
                            placeholder = { Text(stringResource(R.string.med_timetable_time_hint)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandTeal,
                                focusedLabelColor = BrandTeal,
                                cursorColor = BrandTeal,
                            ),
                        )
                    }
                    if (i < scheduledTimes.lastIndex) Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(16.dp))

                // Duration
                Text(stringResource(R.string.med_timetable_duration), fontWeight = FontWeight.SemiBold, color = Color.Black, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = durationDays,
                    onValueChange = { durationDays = it.filter { c -> c.isDigit() }.take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        focusedLabelColor = BrandTeal,
                        cursorColor = BrandTeal,
                    ),
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.med_timetable_nurse_note),
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = durationDays.toIntOrNull() ?: defaultDays
                    val validTimes = scheduledTimes.filter { it.matches(Regex("\\d{2}:\\d{2}")) }
                    if (validTimes.isNotEmpty() && days > 0) {
                        onConfirm(timesPerDay, validTimes, days)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
            ) {
                Text(stringResource(R.string.med_timetable_confirm), color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.med_timetable_cancel), color = Color.Black)
            }
        },
    )
}

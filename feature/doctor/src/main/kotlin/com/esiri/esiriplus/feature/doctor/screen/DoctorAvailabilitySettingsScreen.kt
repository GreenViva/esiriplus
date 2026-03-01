package com.esiri.esiriplus.feature.doctor.screen

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.network.service.AvailabilitySlotRow
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorAvailabilityViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF374151)
private val SuccessGreen = Color(0xFF16A34A)
private val ErrorRed = Color(0xFFDC2626)

private val DAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

@Composable
fun DoctorAvailabilitySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorAvailabilityViewModel = hiltViewModel(),
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
                    contentDescription = "Back",
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "Back", fontSize = 14.sp, color = BrandTeal)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Availability Schedule",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                Button(
                    onClick = viewModel::showAddDialog,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "Add Slot", fontSize = 13.sp, color = Color.White)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Set your weekly recurring availability for appointments",
                fontSize = 14.sp,
                color = SubtitleGrey,
            )
        }

        HorizontalDivider(color = CardBorder, thickness = 1.dp)

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandTeal)
            }
        } else if (uiState.slots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No availability slots configured",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Add slots to let patients book appointments with you.",
                        fontSize = 14.sp,
                        color = SubtitleGrey,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // Group by day of week
            val groupedSlots = uiState.slots.groupBy { it.dayOfWeek }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                (0..6).forEach { day ->
                    val daySlots = groupedSlots[day] ?: return@forEach
                    item(key = "day_$day") {
                        DaySlotGroup(
                            dayName = DAY_NAMES[day],
                            slots = daySlots,
                            onDeleteSlot = viewModel::deleteSlot,
                        )
                    }
                }
            }
        }

        // Error/success messages
        if (uiState.errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ErrorRed.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clickable { viewModel.dismissMessages() },
            ) {
                Text(
                    text = uiState.errorMessage ?: "",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = ErrorRed,
                )
            }
        }
        if (uiState.successMessage != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SuccessGreen.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clickable { viewModel.dismissMessages() },
            ) {
                Text(
                    text = uiState.successMessage ?: "",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = SuccessGreen,
                )
            }
        }
    }

    // Add slot dialog
    if (uiState.showAddDialog) {
        AddSlotDialog(
            dayOfWeek = uiState.editDayOfWeek,
            startTime = uiState.editStartTime,
            endTime = uiState.editEndTime,
            bufferMinutes = uiState.editBufferMinutes,
            onDayChange = viewModel::updateEditDay,
            onStartTimeChange = viewModel::updateEditStartTime,
            onEndTimeChange = viewModel::updateEditEndTime,
            onBufferChange = viewModel::updateEditBufferMinutes,
            onSave = viewModel::saveSlot,
            onDismiss = viewModel::dismissAddDialog,
        )
    }
}

@Composable
private fun DaySlotGroup(
    dayName: String,
    slots: List<AvailabilitySlotRow>,
    onDeleteSlot: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = dayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Spacer(Modifier.height(8.dp))

            slots.forEach { slot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${slot.startTime} - ${slot.endTime}",
                            fontSize = 14.sp,
                            color = Color.Black,
                        )
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BrandTeal.copy(alpha = 0.1f),
                        ) {
                            Text(
                                text = "${slot.bufferMinutes}min buffer",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                color = BrandTeal,
                            )
                        }
                        if (!slot.isActive) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SubtitleGrey.copy(alpha = 0.1f),
                            ) {
                                Text(
                                    text = "Inactive",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    color = SubtitleGrey,
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { onDeleteSlot(slot.slotId) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSlotDialog(
    dayOfWeek: Int,
    startTime: String,
    endTime: String,
    bufferMinutes: Int,
    onDayChange: (Int) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onBufferChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDayDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Availability Slot", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Day picker
                Text(text = "Day of Week", fontSize = 13.sp, color = SubtitleGrey)
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDayDropdown = true },
                    ) {
                        Text(
                            text = DAY_NAMES[dayOfWeek],
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp,
                            color = Color.Black,
                        )
                    }
                    DropdownMenu(
                        expanded = showDayDropdown,
                        onDismissRequest = { showDayDropdown = false },
                    ) {
                        DAY_NAMES.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onDayChange(index)
                                    showDayDropdown = false
                                },
                            )
                        }
                    }
                }

                // Start time
                OutlinedTextField(
                    value = startTime,
                    onValueChange = onStartTimeChange,
                    label = { Text("Start Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = CardBorder,
                    ),
                    singleLine = true,
                )

                // End time
                OutlinedTextField(
                    value = endTime,
                    onValueChange = onEndTimeChange,
                    label = { Text("End Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = CardBorder,
                    ),
                    singleLine = true,
                )

                // Buffer
                OutlinedTextField(
                    value = bufferMinutes.toString(),
                    onValueChange = { onBufferChange(it.toIntOrNull() ?: 5) },
                    label = { Text("Buffer Minutes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = CardBorder,
                    ),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SubtitleGrey)
            }
        },
    )
}

package com.esiri.esiriplus.feature.doctor.screen

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
// import androidx.compose.material3.ModalBottomSheet  // replaced with Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.esiri.esiriplus.core.ui.ScrollIndicatorBox
import com.esiri.esiriplus.feature.doctor.R
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorReportViewModel
import com.esiri.esiriplus.feature.doctor.viewmodel.Prescription
import kotlinx.coroutines.delay

private val BrandTeal = Color(0xFF2A9D8F)
private val SubtitleGrey = Color(0xFF6B7280)
private val CardBorder = Color(0xFFE5E7EB)
private val SuccessGreen = Color(0xFF16A34A)
private val ErrorRed = Color(0xFFDC2626)

/**
 * Bottom sheet for the consultation report form.
 * Shown after doctor ends consultation or taps "Write Report".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationReportBottomSheet(
    consultationId: String,
    onDismiss: () -> Unit,
    onReportSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            delay(1500)
            onReportSubmitted()
        }
    }

    // Block back navigation until report is done
    BackHandler(enabled = !uiState.submitSuccess) { /* no-op: must complete report */ }

    // Full-screen surface — cannot be dismissed by dragging, swiping, or tapping outside.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.White,
    ) {
        // Dosage configuration dialog
        val pendingMed = uiState.pendingMedication
        if (pendingMed != null) {
            DosageConfigDialog(
                medicationName = pendingMed,
                isInjectable = Prescription.isInjectable(pendingMed),
                onConfirm = { form, qty, times, days, route ->
                    viewModel.confirmPrescription(form, qty, times, days, route)
                },
                onDismiss = { viewModel.cancelPendingMedication() },
            )
        }

        // Medication timetable dialog (Royal only).
        // If a timetable for this medication already exists, pre-populate the
        // dialog with its values so the doctor can edit instead of starting
        // over with default times.
        if (uiState.showTimetableDialog && uiState.timetableForPrescription != null) {
            val rx = uiState.timetableForPrescription!!
            val existing = uiState.medicationTimetables
                .firstOrNull { it.medicationName == rx.medication }
            MedicationTimetableDialog(
                medicationName = rx.medication,
                defaultDays = rx.days,
                existingTimesPerDay = existing?.timesPerDay,
                existingScheduledTimes = existing?.scheduledTimes,
                existingDurationDays = existing?.durationDays,
                onConfirm = { timesPerDay, scheduledTimes, durationDays ->
                    viewModel.confirmTimetable(timesPerDay, scheduledTimes, durationDays)
                },
                onDismiss = { viewModel.closeTimetableDialog() },
            )
        }

        if (uiState.submitSuccess) {
            // Success state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(80.dp),
                    )
                    Text(
                        text = stringResource(R.string.report_submitted_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Text(
                        text = stringResource(R.string.report_submitted_message),
                        fontSize = 14.sp,
                        color = SubtitleGrey,
                    )
                }
            }
        } else {
            val sheetScrollState = rememberScrollState()
            ScrollIndicatorBox(scrollState = sheetScrollState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(sheetScrollState)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                // Header with icon
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(BrandTeal.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.report_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.report_instruction),
                        fontSize = 13.sp,
                        color = SubtitleGrey,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Patient Age & Gender
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Patient Age", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = uiState.patientAge,
                            onValueChange = viewModel::updatePatientAge,
                            placeholder = { Text("e.g. 35", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandTeal,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Patient Gender", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Male", "Female").forEach { gender ->
                                val selected = uiState.patientGender == gender
                                Surface(
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) BrandTeal else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) BrandTeal else MaterialTheme.colorScheme.outline,
                                    ),
                                    onClick = { viewModel.updatePatientGender(gender) },
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = gender,
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) Color.White else Color.Black,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Diagnosed Problem (required)
                RequiredLabel(stringResource(R.string.report_diagnosed_problem))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.diagnosedProblem,
                    onValueChange = viewModel::updateDiagnosedProblem,
                    placeholder = { Text(stringResource(R.string.report_diagnosed_placeholder), color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    colors = textFieldColors(),
                )

                Spacer(Modifier.height(16.dp))

                // Category (required, dropdown)
                RequiredLabel(stringResource(R.string.report_category))
                Spacer(Modifier.height(6.dp))
                CategoryDropdown(
                    selected = uiState.category,
                    onSelected = viewModel::updateCategory,
                )

                // Show "Other" text field when category is "Other"
                if (uiState.category == "Other") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.otherCategory,
                        onValueChange = viewModel::updateOtherCategory,
                        placeholder = { Text(stringResource(R.string.report_other_category_placeholder), color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = textFieldColors(),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Severity Level
                Text(
                    text = stringResource(R.string.report_severity),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                Spacer(Modifier.height(6.dp))
                SeverityDropdown(
                    selected = uiState.severity,
                    onSelected = viewModel::updateSeverity,
                )

                Spacer(Modifier.height(16.dp))

                // Decision / Treatment Plan (required)
                RequiredLabel(stringResource(R.string.report_treatment_plan))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.treatmentPlan,
                    onValueChange = viewModel::updateTreatmentPlan,
                    placeholder = { Text(stringResource(R.string.report_treatment_placeholder), color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    colors = textFieldColors(),
                )

                Spacer(Modifier.height(16.dp))

                // Further Notes (optional)
                Text(
                    text = stringResource(R.string.report_further_notes),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.furtherNotes,
                    onValueChange = viewModel::updateFurtherNotes,
                    placeholder = { Text(stringResource(R.string.report_notes_placeholder), color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    colors = textFieldColors(),
                )

                // ── Medication / Prescription (optional) ──
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Medication / Prescription (Optional)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                Spacer(Modifier.height(6.dp))

                // Search field for medications
                var medSearchExpanded by remember { mutableStateOf(false) }
                val prescribedNames = uiState.prescriptions.map { it.medication }.toSet()
                val filteredMeds = remember(uiState.medicationSearchQuery, prescribedNames) {
                    if (uiState.medicationSearchQuery.length < 2) emptyList()
                    else DoctorReportViewModel.MEDICATIONS.filter {
                        it.contains(uiState.medicationSearchQuery, ignoreCase = true) &&
                            it !in prescribedNames
                    }.take(6)
                }

                OutlinedTextField(
                    value = uiState.medicationSearchQuery,
                    onValueChange = {
                        viewModel.updateMedicationSearch(it)
                        medSearchExpanded = it.length >= 2
                    },
                    placeholder = { Text("Search medication...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = textFieldColors(),
                )

                // Dropdown results
                if (medSearchExpanded && filteredMeds.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Column {
                            filteredMeds.forEach { med ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectMedication(med)
                                            medSearchExpanded = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = BrandTeal,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = med,
                                        fontSize = 13.sp,
                                        color = Color.Black,
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected prescriptions with dosage info
                if (uiState.prescriptions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.prescriptions.forEach { rx ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BrandTeal.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rx.medication,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.Black,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (rx.form) {
                                                "Tablets" -> BrandTeal.copy(alpha = 0.12f)
                                                "Injection" -> Color(0xFFEF4444).copy(alpha = 0.12f)
                                                else -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                                            },
                                        ) {
                                            Text(
                                                text = if (rx.form == "Injection") "${rx.form} (${rx.route})" else rx.form,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = when (rx.form) {
                                                    "Tablets" -> BrandTeal
                                                    "Injection" -> Color(0xFFEF4444)
                                                    else -> Color(0xFFB45309)
                                                },
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = rx.displayText(),
                                            fontSize = 12.sp,
                                            color = SubtitleGrey,
                                        )
                                        // Timetable badge or button (Royal only)
                                        if (uiState.isRoyalTier) {
                                            val hasTimetable = uiState.medicationTimetables.any { it.medicationName == rx.medication }
                                            Spacer(Modifier.height(6.dp))
                                            if (hasTimetable) {
                                                val tt = uiState.medicationTimetables.first { it.medicationName == rx.medication }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.med_nurse_reminder_badge, tt.scheduledTimes.joinToString(", "), tt.durationDays),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF10B981),
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.med_set_nurse_reminder),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = BrandTeal,
                                                    modifier = Modifier.clickable { viewModel.openTimetableDialog(rx) },
                                                )
                                            }
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = ErrorRed.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { viewModel.removePrescription(rx.medication) },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Follow-up checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleFollowUp() },
                ) {
                    Checkbox(
                        checked = uiState.followUpRecommended,
                        onCheckedChange = { viewModel.toggleFollowUp() },
                        colors = CheckboxDefaults.colors(checkedColor = BrandTeal),
                    )
                    Text(
                        text = stringResource(R.string.report_follow_up),
                        fontSize = 14.sp,
                        color = Color.Black,
                    )
                }

                // Error message
                if (uiState.errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ErrorRed.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = ErrorRed,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Submit & Generate Report button
                Button(
                    onClick = viewModel::submitReport,
                    enabled = !uiState.isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.report_generating))
                    } else {
                        Text(
                            text = stringResource(R.string.report_submit_button),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Cancel button
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !uiState.isSubmitting,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            } // ScrollIndicatorBox
        }
    }
}

@Composable
private fun RequiredLabel(text: String) {
    Row {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "*",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ErrorRed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.ifBlank { "" },
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(stringResource(R.string.report_category_placeholder), color = Color.Gray) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(10.dp),
            colors = textFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DoctorReportViewModel.CATEGORIES.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category, color = Color.Black) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeverityDropdown(
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(10.dp),
            colors = textFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DoctorReportViewModel.SEVERITIES.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level, color = Color.Black) },
                    onClick = {
                        onSelected(level)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandTeal,
    unfocusedBorderColor = CardBorder,
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
)

// Keep as a standalone screen for the DoctorReportRoute (manual navigation)
@Composable
fun DoctorReportScreen(
    onReportSubmitted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            delay(1500)
            onReportSubmitted()
        }
    }

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
        }

        if (uiState.submitSuccess) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(80.dp),
                    )
                    Text(
                        text = stringResource(R.string.report_submitted_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Text(
                        text = stringResource(R.string.report_returning),
                        fontSize = 14.sp,
                        color = SubtitleGrey,
                    )
                }
            }
        } else if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandTeal)
            }
        } else {
            // Reuse the same form content in a scrollable column
            Text(
                text = stringResource(R.string.report_moved_hint),
                modifier = Modifier.padding(20.dp),
                color = SubtitleGrey,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DosageConfigDialog(
    medicationName: String,
    isInjectable: Boolean,
    onConfirm: (form: String, quantity: Int, timesPerDay: Int, days: Int, route: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // If injectable, pre-select "Injection"; otherwise empty so doctor picks Tablets/Syrup
    var selectedForm by remember { mutableStateOf(if (isInjectable) "Injection" else "") }
    var selectedRoute by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var timesPerDay by remember { mutableStateOf("1") }
    var days by remember { mutableStateOf("1") }

    val formOptions = if (isInjectable) listOf("Injection") else listOf("Tablets", "Syrup")
    val isInjectionForm = selectedForm == "Injection"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Column {
                Text(
                    text = "Dosage Instructions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = medicationName,
                    fontSize = 13.sp,
                    color = SubtitleGrey,
                )
                if (isInjectable) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = "Injectable medication",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444),
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Form selection
                Text("Form", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    formOptions.forEach { form ->
                        val isSelected = selectedForm == form
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BrandTeal else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) BrandTeal else CardBorder,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedForm = form },
                        ) {
                            Text(
                                text = form,
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else Color.Black,
                            )
                        }
                    }
                }

                if (selectedForm.isNotEmpty()) {
                    // ── Injection-specific: Route selector ──
                    if (isInjectionForm) {
                        Text("Route of Administration", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("IM" to "Intramuscular", "IV" to "Intravenous", "SC" to "Subcutaneous").forEach { (abbr, full) ->
                                val isSelected = selectedRoute == abbr
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) BrandTeal else Color.Transparent,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) BrandTeal else CardBorder,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedRoute = abbr },
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    ) {
                                        Text(
                                            text = abbr,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color.Black,
                                        )
                                        Text(
                                            text = full,
                                            fontSize = 9.sp,
                                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else SubtitleGrey,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Tablets/Syrup: Quantity per dose ──
                    if (!isInjectionForm) {
                        Text(
                            text = if (selectedForm == "Tablets") "How many tablets per dose?" else "How many ml per dose?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                        )
                        StepperRow(
                            value = quantity,
                            onValueChange = { quantity = it },
                            maxLength = 3,
                            unitLabel = if (selectedForm == "Tablets") "tablet(s)" else "ml",
                        )
                    }

                    // Times per day
                    Text("How many times per day?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                    StepperRow(
                        value = timesPerDay,
                        onValueChange = { timesPerDay = it },
                        maxLength = 2,
                        unitLabel = "time(s)/day",
                    )

                    // For how many days
                    Text("For how many days?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                    StepperRow(
                        value = days,
                        onValueChange = { days = it },
                        maxLength = 3,
                        unitLabel = "day(s)",
                    )

                    // Preview
                    val previewQty = quantity.toIntOrNull() ?: 1
                    val previewTimes = timesPerDay.toIntOrNull() ?: 1
                    val previewDays = days.toIntOrNull() ?: 1
                    val preview = Prescription(medicationName, selectedForm, previewQty, previewTimes, previewDays, selectedRoute)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BrandTeal.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = preview.displayText(),
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = BrandTeal,
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isValid = if (isInjectionForm) {
                selectedForm.isNotEmpty() && selectedRoute.isNotEmpty()
            } else {
                selectedForm.isNotEmpty() && (quantity.toIntOrNull() ?: 0) > 0
            }
            Button(
                onClick = {
                    val qty = quantity.toIntOrNull() ?: 1
                    val times = timesPerDay.toIntOrNull() ?: 1
                    val d = days.toIntOrNull() ?: 1
                    onConfirm(selectedForm, qty, times, d, selectedRoute)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Add Prescription", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SubtitleGrey)
            }
        },
    )
}

@Composable
private fun StepperRow(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    unitLabel: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = BrandTeal,
            modifier = Modifier
                .size(36.dp)
                .clickable {
                    val v = (value.toIntOrNull() ?: 1) - 1
                    if (v >= 1) onValueChange(v.toString())
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= maxLength) onValueChange(it) },
            modifier = Modifier.width(70.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            shape = RoundedCornerShape(10.dp),
            colors = textFieldColors(),
        )
        Surface(
            shape = CircleShape,
            color = BrandTeal,
            modifier = Modifier
                .size(36.dp)
                .clickable { onValueChange(((value.toIntOrNull() ?: 1) + 1).toString()) },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Text(unitLabel, fontSize = 13.sp, color = SubtitleGrey)
    }
}

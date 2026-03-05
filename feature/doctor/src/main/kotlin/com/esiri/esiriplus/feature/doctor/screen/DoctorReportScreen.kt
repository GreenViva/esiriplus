package com.esiri.esiriplus.feature.doctor.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
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
import com.esiri.esiriplus.feature.doctor.R
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorReportViewModel
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            delay(1500)
            onReportSubmitted()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = modifier,
    ) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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

                Spacer(Modifier.height(24.dp))

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

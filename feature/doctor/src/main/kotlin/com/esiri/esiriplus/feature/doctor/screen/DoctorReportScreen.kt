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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorReportViewModel
import kotlinx.coroutines.delay

private val BrandTeal = Color(0xFF2A9D8F)
private val SubtitleGrey = Color(0xFF374151)
private val SuccessGreen = Color(0xFF16A34A)
private val CardBorder = Color(0xFFE5E7EB)

@Composable
fun DoctorReportScreen(
    onReportSubmitted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate back on success
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
                    contentDescription = "Back",
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "Back", fontSize = 14.sp, color = BrandTeal)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Consultation Report",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        if (uiState.submitSuccess) {
            // Success state
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
                        text = "Report Submitted",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Text(
                        text = "Returning to dashboard...",
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                // Service type badge
                if (uiState.serviceType.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BrandTeal.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = uiState.serviceType
                                .replaceFirstChar { it.uppercase() }
                                .replace("_", " "),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = BrandTeal,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // AI Report section
                if (uiState.aiReport != null) {
                    AiReportCard(uiState.aiReport!!)
                    Spacer(Modifier.height(16.dp))
                }

                // Diagnosis field
                ReportTextField(
                    label = "Diagnosis / Assessment",
                    value = uiState.diagnosis,
                    onValueChange = viewModel::updateDiagnosis,
                    placeholder = "Enter your clinical assessment...",
                    minLines = 3,
                )

                Spacer(Modifier.height(16.dp))

                // Prescription field
                ReportTextField(
                    label = "Prescription",
                    value = uiState.prescription,
                    onValueChange = viewModel::updatePrescription,
                    placeholder = "Medications, dosage, frequency...",
                    minLines = 3,
                )

                Spacer(Modifier.height(16.dp))

                // Additional notes field
                ReportTextField(
                    label = "Additional Notes",
                    value = uiState.additionalNotes,
                    onValueChange = viewModel::updateAdditionalNotes,
                    placeholder = "Follow-up instructions, referrals...",
                    minLines = 3,
                )

                // Error message
                if (uiState.errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = Color(0xFFDC2626),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Generate AI Report button
                OutlinedButton(
                    onClick = viewModel::generateAiReport,
                    enabled = !uiState.isGeneratingReport && !uiState.isSubmitting,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (uiState.isGeneratingReport) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = BrandTeal,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Generating...", color = BrandTeal)
                    } else {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = BrandTeal,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (uiState.aiReport != null) "Regenerate AI Report" else "Generate AI Report",
                            color = BrandTeal,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Submit button
                Button(
                    onClick = viewModel::submitReport,
                    enabled = !uiState.isSubmitting && !uiState.isGeneratingReport,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Submitting...")
                    } else {
                        Text("Submit Report", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReportTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int,
) {
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Black,
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.Gray) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandTeal,
            unfocusedBorderColor = CardBorder,
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
        ),
    )
}

@Composable
private fun AiReportCard(report: com.esiri.esiriplus.feature.doctor.viewmodel.AiReportContent) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandTeal.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "AI-Generated Report",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandTeal,
                )
            }

            Spacer(Modifier.height(12.dp))

            ReportSection("Chief Complaint", report.chiefComplaint)
            ReportSection("History", report.history)
            ReportSection("Assessment", report.assessment)
            ReportSection("Plan", report.plan)
            ReportSection("Follow-up", report.followUp)
        }
    }
}

@Composable
private fun ReportSection(title: String, content: String) {
    if (content.isBlank()) return
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.Black,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = content,
        fontSize = 13.sp,
        color = SubtitleGrey,
    )
    Spacer(Modifier.height(10.dp))
}

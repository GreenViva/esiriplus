package com.esiri.esiriplus.feature.patient.screen

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.PatientReport
import com.esiri.esiriplus.feature.patient.util.ReportPdfGenerator
import com.esiri.esiriplus.feature.patient.viewmodel.ReportDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val SectionBg = Color(0xFFF8FFFE)
private val LabelGrey = Color(0xFF6B7280)
private val DisclaimerBg = Color(0xFFFFFBEB)

@Composable
fun ReportDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Consultation Report",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(1f),
            )
            if (uiState.report != null) {
                IconButton(onClick = {
                    scope.launch {
                        shareReportPdf(context, uiState.report!!)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = BrandTeal,
                    )
                }
            }
        }

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
                        text = uiState.error ?: "Error",
                        color = Color.Red,
                        fontSize = 14.sp,
                    )
                }
            }
            uiState.report != null -> {
                val report = uiState.report!!
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ReportContent(report)
                }

                // Download PDF button
                Button(
                    onClick = {
                        scope.launch {
                            shareReportPdf(context, report)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Download PDF",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportContent(report: PatientReport) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
    ) {
        // Teal header
        Surface(
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            color = BrandTeal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "ESIRII HEALTH",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Telemedicine Consultation Report",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }

        // Consultation info bar
        Surface(
            color = SectionBg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "CONSULTATION REPORT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTeal,
                        letterSpacing = 1.sp,
                    )
                    if (report.verificationCode.isNotBlank()) {
                        Text(
                            text = "Ref: ${report.verificationCode}",
                            fontSize = 11.sp,
                            color = LabelGrey,
                        )
                    }
                }
                if (report.consultationDate > 0) {
                    Text(
                        text = formatDate(report.consultationDate),
                        fontSize = 12.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        HorizontalDivider(color = BrandTeal, thickness = 2.dp)

        Spacer(Modifier.height(16.dp))

        // Section 1: Patient Information
        SectionHeader("1. Patient Information")
        Spacer(Modifier.height(8.dp))
        if (report.patientSessionId.isNotBlank()) {
            InfoRow("Patient ID", report.patientSessionId.take(12) + "...")
        }
        if (report.consultationDate > 0) {
            InfoRow("Consultation Date", formatDate(report.consultationDate))
        }
        InfoRow("Consultation Type", "Telemedicine")

        Spacer(Modifier.height(20.dp))

        // Section 2: Presenting Symptoms
        SectionHeader("2. Presenting Symptoms")
        Spacer(Modifier.height(8.dp))
        ProseBlock(
            report.presentingSymptoms.ifBlank { "No presenting symptoms recorded." },
        )

        Spacer(Modifier.height(20.dp))

        // Section 3: Diagnosis and Assessment
        SectionHeader("3. Diagnosis and Assessment")
        Spacer(Modifier.height(8.dp))
        if (report.diagnosedProblem.isNotBlank()) {
            InfoRow("Primary Diagnosis", report.diagnosedProblem)
        }
        if (report.category.isNotBlank()) {
            InfoRow("Category", report.category)
        }
        if (report.severity.isNotBlank()) {
            InfoRow("Severity", report.severity)
        }
        if (report.diagnosisAssessment.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ProseBlock(report.diagnosisAssessment)
        }

        Spacer(Modifier.height(20.dp))

        // Section 4: Treatment Plan
        SectionHeader("4. Treatment Plan")
        Spacer(Modifier.height(8.dp))
        ProseBlock(
            report.treatmentPlan.ifBlank { "No treatment plan specified." },
        )

        Spacer(Modifier.height(20.dp))

        // Section 5: Follow-up Instructions
        SectionHeader("5. Follow-up Instructions")
        Spacer(Modifier.height(8.dp))
        InfoRow(
            "Follow-up Recommended",
            if (report.followUpRecommended) "Yes" else "No",
        )
        if (report.followUpInstructions.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ProseBlock(report.followUpInstructions)
        }
        if (report.furtherNotes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Additional Notes:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
            )
            Spacer(Modifier.height(4.dp))
            ProseBlock(report.furtherNotes)
        }

        Spacer(Modifier.height(20.dp))

        // Section 6: Telemedicine Disclaimer
        SectionHeader("6. Telemedicine Disclaimer")
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DisclaimerBg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "This consultation was conducted via telemedicine. The assessment and recommendations " +
                    "are based on the information provided during the virtual consultation. If symptoms persist " +
                    "or worsen, please seek in-person medical attention.",
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp,
                color = Color(0xFF92400E),
                fontStyle = FontStyle.Italic,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Electronic Signature
        HorizontalDivider(color = Color(0xFFE5E7EB))
        Spacer(Modifier.height(12.dp))
        if (report.doctorName.isNotBlank()) {
            Text(
                text = "Dr. ${report.doctorName}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Text(
                text = "Attending Physician",
                fontSize = 13.sp,
                color = LabelGrey,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = "Electronically signed",
            fontSize = 11.sp,
            color = LabelGrey,
            fontStyle = FontStyle.Italic,
        )

        Spacer(Modifier.height(20.dp))

        // Footer
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF3F4F6),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Generated by ESIRII Health Platform",
                    fontSize = 11.sp,
                    color = LabelGrey,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = BrandTeal,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = "$label:",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = LabelGrey,
            modifier = Modifier.width(160.dp),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProseBlock(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SectionBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            fontSize = 13.sp,
            color = Color.Black,
            lineHeight = 20.sp,
        )
    }
}

private suspend fun shareReportPdf(context: Context, report: PatientReport) {
    withContext(Dispatchers.IO) {
        try {
            val pdfFile = ReportPdfGenerator.generate(context, report)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Share Report"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to generate PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun formatDate(millis: Long): String {
    if (millis == 0L) return ""
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

package com.esiri.esiriplus.feature.auth.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.ui.GradientBackground
import com.esiri.esiriplus.feature.auth.viewmodel.PatientSetupViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val DarkText = Color.Black
private val SubtitleGray = Color.Black
private val LabelColor = Color.Black
private val CardBg = Color(0xFF2A9D8F)
private val SectionBg = Color(0xFFF9FAFB)
private val SectionBorder = Color(0xFFE5E7EB)
private val WarningAmber = Color(0xFFF59E0B)
private val SuccessGreen = Color(0xFF10B981)

private val AGE_GROUPS = listOf("Under 18", "18-25", "26-35", "36-45", "46-55", "56-65", "65+")
private val BLOOD_TYPES = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientSetupScreen(
    onComplete: () -> Unit,
    onNavigateToRecoveryQuestions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    GradientBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Patient Setup",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Step 3 of 3",
                    fontSize = 13.sp,
                    color = SubtitleGray,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            if (state.isCreatingSession) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BrandTeal)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Creating your account...",
                            fontSize = 14.sp,
                            color = SubtitleGray,
                        )
                    }
                }
            } else if (state.sessionError != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.sessionError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::retryCreateSession,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else {

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Patient ID Card
                item {
                    PatientIdCard(
                        patientId = state.patientId,
                        onCopy = {
                            copyToClipboard(context, state.patientId)
                        },
                    )
                }

                // Download ID Card button
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.downloadIdCard(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = state.canDownloadPdf && !state.isGeneratingPdf,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (state.canDownloadPdf) BrandTeal else SubtitleGray.copy(alpha = 0.4f),
                            ),
                        ) {
                            if (state.isGeneratingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandTeal,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Generating PDF...",
                                    color = BrandTeal,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                            } else {
                                Text(
                                    text = "\u2B07  Download ID Card",
                                    color = if (state.canDownloadPdf) BrandTeal else SubtitleGray.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                        if (!state.canDownloadPdf) {
                            Text(
                                text = "Fill Sex and Age Group to download",
                                fontSize = 12.sp,
                                color = SubtitleGray,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        state.pdfError?.let { error ->
                            Text(
                                text = error,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                // Recovery Questions Card
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    RecoveryQuestionsCard(
                        isCompleted = state.recoveryQuestionsCompleted,
                        onClick = onNavigateToRecoveryQuestions,
                    )
                }

                // Health Profile Section (in container)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, SectionBorder, RoundedCornerShape(16.dp))
                            .background(SectionBg)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Health Profile (Optional)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                        )
                        Text(
                            text = "Help your doctor to serve you better",
                            fontSize = 14.sp,
                            color = SubtitleGray,
                        )

                        // Sex selection
                        Column {
                            Text(
                                text = "Sex",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LabelColor,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                listOf("Male", "Female").forEach { sex ->
                                    FilterChip(
                                        selected = state.sex == sex,
                                        onClick = { viewModel.onSexChanged(sex) },
                                        label = {
                                            Text(
                                                text = sex,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandTeal,
                                            selectedLabelColor = Color.White,
                                        ),
                                        enabled = !state.isSaving,
                                    )
                                }
                            }
                        }

                        // Age Group dropdown
                        Column {
                            Text(
                                text = "Age Group",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LabelColor,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            var ageExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = ageExpanded,
                                onExpandedChange = { ageExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = state.ageGroup,
                                    onValueChange = {},
                                    readOnly = true,
                                    placeholder = {
                                        Text(
                                            text = "Select age group",
                                            color = SubtitleGray,
                                            fontSize = 15.sp,
                                        )
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = ageExpanded,
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    enabled = !state.isSaving,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                ExposedDropdownMenu(
                                    expanded = ageExpanded,
                                    onDismissRequest = { ageExpanded = false },
                                ) {
                                    AGE_GROUPS.forEach { group ->
                                        DropdownMenuItem(
                                            text = { Text(group) },
                                            onClick = {
                                                viewModel.onAgeGroupChanged(group)
                                                ageExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // Blood Type dropdown
                        Column {
                            Text(
                                text = "Blood Type",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LabelColor,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            var bloodExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = bloodExpanded,
                                onExpandedChange = { bloodExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = state.bloodType,
                                    onValueChange = {},
                                    readOnly = true,
                                    placeholder = {
                                        Text(
                                            text = "Select blood type",
                                            color = SubtitleGray,
                                            fontSize = 15.sp,
                                        )
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = bloodExpanded,
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    enabled = !state.isSaving,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                ExposedDropdownMenu(
                                    expanded = bloodExpanded,
                                    onDismissRequest = { bloodExpanded = false },
                                ) {
                                    BLOOD_TYPES.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type) },
                                            onClick = {
                                                viewModel.onBloodTypeChanged(type)
                                                bloodExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // Allergies
                        Column {
                            Text(
                                text = "Allergies",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LabelColor,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            OutlinedTextField(
                                value = state.allergies,
                                onValueChange = viewModel::onAllergiesChanged,
                                placeholder = {
                                    Text(
                                        text = "e.g. Penicillin, Peanuts",
                                        color = SubtitleGray,
                                        fontSize = 15.sp,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                enabled = !state.isSaving,
                                shape = RoundedCornerShape(10.dp),
                            )
                        }

                        // Chronic Conditions
                        Column {
                            Text(
                                text = "Chronic Conditions",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LabelColor,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            OutlinedTextField(
                                value = state.chronicConditions,
                                onValueChange = viewModel::onChronicConditionsChanged,
                                placeholder = {
                                    Text(
                                        text = "e.g. Diabetes, Hypertension",
                                        color = SubtitleGray,
                                        fontSize = 15.sp,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                enabled = !state.isSaving,
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                }

                // Error message
                if (state.saveError != null) {
                    item {
                        Text(
                            text = state.saveError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Bottom spacing for sticky button
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            } // end else (session created)

            // Sticky bottom button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = viewModel::onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = Color.White,
                    ),
                    enabled = state.patientId.isNotBlank() && !state.isSaving,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(
                            text = "Continue",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientIdCard(
    patientId: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(24.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Your Patient ID",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = patientId,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onCopy),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Copy",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Save this ID for future access",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RecoveryQuestionsCard(
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isCompleted) SuccessGreen.copy(alpha = 0.4f) else SectionBorder,
                RoundedCornerShape(16.dp),
            )
            .background(if (isCompleted) SuccessGreen.copy(alpha = 0.05f) else SectionBg)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.recovery_questions_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )
        }

        if (isCompleted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.recovery_questions_saved),
                fontSize = 14.sp,
                color = SuccessGreen,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.recovery_questions_hint),
                fontSize = 14.sp,
                color = SubtitleGray,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.recovery_questions_warning),
                    fontSize = 13.sp,
                    color = WarningAmber,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Set Up \u203A",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandTeal,
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Patient ID", text))
    Toast.makeText(context, "Patient ID copied", Toast.LENGTH_SHORT).show()
}

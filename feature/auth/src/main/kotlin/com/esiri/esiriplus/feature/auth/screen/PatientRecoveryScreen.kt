package com.esiri.esiriplus.feature.auth.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.esiri.esiriplus.core.common.util.SecurityQuestions
import com.esiri.esiriplus.feature.auth.viewmodel.PatientRecoveryViewModel

private val BrandTeal = Color(0xFF2A9D8F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientRecoveryScreen(
    onRecovered: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientRecoveryViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.value

    LaunchedEffect(state.continueClicked) {
        if (state.continueClicked) {
            onRecovered()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recover Patient ID") },
                navigationIcon = {
                    if (state.recoveredPatientId == null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                // Success state — show recovered Patient ID
                state.recoveredPatientId != null -> {
                    RecoverySuccessContent(
                        patientId = state.recoveredPatientId,
                        onContinue = viewModel::onContinueToDashboard,
                    )
                }

                // Rate limited
                state.isRateLimited -> {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "Too many attempts",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "You have exceeded the maximum number of recovery attempts. Please try again in 24 hours.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black,
                    )
                }

                // Security questions
                else -> {
                    Text(
                        text = "Answer all 5 security questions to recover your account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = {
                            (state.currentQuestionIndex + 1).toFloat() / SecurityQuestions.ALL.size
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = BrandTeal,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Question ${state.currentQuestionIndex + 1} of ${SecurityQuestions.ALL.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black,
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    val questionKey = SecurityQuestions.ALL[state.currentQuestionIndex]
                    val questionLabel = SecurityQuestions.LABELS[questionKey].orEmpty()

                    Text(
                        text = questionLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = state.currentAnswer,
                        onValueChange = viewModel::onAnswerChanged,
                        label = { Text("Your answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (state.error != null) {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${state.remainingAttempts} attempt(s) remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    val isLastQuestion =
                        state.currentQuestionIndex == SecurityQuestions.ALL.lastIndex
                    Button(
                        onClick = viewModel::onNext,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.currentAnswer.isNotBlank() && !state.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Text(
                                if (isLastQuestion) "Recover Account" else "Next",
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoverySuccessContent(
    patientId: String,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(32.dp))

    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = BrandTeal,
        modifier = Modifier.size(64.dp),
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Account Recovered!",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Your Patient ID has been recovered. Please save it somewhere safe.",
        fontSize = 14.sp,
        color = Color.Black,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(24.dp))

    // Patient ID card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = BrandTeal.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 2.dp,
                color = BrandTeal,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Your Patient ID",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = patientId,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = BrandTeal,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Patient ID", patientId),
                )
                Toast.makeText(context, "Patient ID copied!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandTeal,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Copy to Clipboard")
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onContinue,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandTeal,
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = "Continue to Dashboard",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}

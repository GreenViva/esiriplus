package com.esiri.esiriplus.feature.auth.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.esiri.esiriplus.core.common.util.SecurityQuestions
import com.esiri.esiriplus.feature.auth.viewmodel.PatientRecoveryViewModel

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

    LaunchedEffect(state.recoveredPatientId) {
        if (state.recoveredPatientId != null) {
            onRecovered()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recover Patient ID") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (state.isRateLimited) {
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
                )
            } else {
                // Progress indicator
                LinearProgressIndicator(
                    progress = {
                        (state.currentQuestionIndex + 1).toFloat() / SecurityQuestions.ALL.size
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Question ${state.currentQuestionIndex + 1} of ${SecurityQuestions.ALL.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(32.dp))

                val questionKey = SecurityQuestions.ALL[state.currentQuestionIndex]
                val questionLabel = SecurityQuestions.LABELS[questionKey].orEmpty()

                Text(
                    text = questionLabel,
                    style = MaterialTheme.typography.titleMedium,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val isLastQuestion =
                    state.currentQuestionIndex == SecurityQuestions.ALL.lastIndex
                Button(
                    onClick = viewModel::onNext,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.currentAnswer.isNotBlank() && !state.isLoading,
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(if (isLastQuestion) "Recover Account" else "Next")
                    }
                }
            }
        }
    }
}

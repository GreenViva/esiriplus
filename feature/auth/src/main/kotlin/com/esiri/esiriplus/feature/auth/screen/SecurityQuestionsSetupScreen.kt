package com.esiri.esiriplus.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.esiri.esiriplus.core.common.util.SecurityQuestions
import com.esiri.esiriplus.feature.auth.ui.GradientBackground
import com.esiri.esiriplus.feature.auth.viewmodel.SecurityQuestionsSetupViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val DarkText = Color.Black
private val SubtitleGray = Color.Black

@Composable
fun SecurityQuestionsSetupScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecurityQuestionsSetupViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.value

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            onComplete()
        }
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
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recovery Questions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Set Up Security Questions",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "These questions will help you recover your Patient ID if you forget it.",
                    fontSize = 14.sp,
                    color = SubtitleGray,
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Progress
                LinearProgressIndicator(
                    progress = {
                        (state.currentQuestionIndex + 1).toFloat() / SecurityQuestions.ALL.size
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = BrandTeal,
                    trackColor = Color(0xFFE5E7EB),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Question ${state.currentQuestionIndex + 1} of ${SecurityQuestions.ALL.size}",
                    fontSize = 13.sp,
                    color = SubtitleGray,
                )
                Spacer(modifier = Modifier.height(32.dp))

                val questionKey = SecurityQuestions.ALL[state.currentQuestionIndex]
                val questionLabel = SecurityQuestions.LABELS[questionKey].orEmpty()

                Text(
                    text = questionLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText,
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.currentAnswer,
                    onValueChange = viewModel::onAnswerChanged,
                    label = {
                        Text(
                            "Your answer",
                            color = SubtitleGray,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Sticky bottom buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val isLastQuestion =
                    state.currentQuestionIndex == SecurityQuestions.ALL.lastIndex
                Button(
                    onClick = viewModel::onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = Color.White,
                    ),
                    enabled = state.currentAnswer.isNotBlank() && !state.isLoading,
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(
                            text = if (isLastQuestion) "Save Security Questions" else "Next",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onSkip) {
                    Text(
                        text = "Skip for now",
                        color = SubtitleGray,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

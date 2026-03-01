package com.esiri.esiriplus.feature.patient.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.ConsultationPhase
import com.esiri.esiriplus.feature.chat.ui.ChatContent
import com.esiri.esiriplus.feature.chat.ui.ConsultationTimerBar
import com.esiri.esiriplus.feature.chat.ui.GracePeriodBanner
import com.esiri.esiriplus.feature.chat.ui.PatientExtensionPrompt
import com.esiri.esiriplus.feature.patient.viewmodel.PatientConsultationViewModel

@Composable
fun PatientConsultationScreen(
    onNavigateToPayment: (String) -> Unit,
    onNavigateToExtensionPayment: (String, Int, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientConsultationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()

    // Block back navigation during active consultation
    val isActive = sessionState.phase != ConsultationPhase.COMPLETED
    BackHandler(enabled = isActive) {
        // Swallow back press — patient must wait for doctor to end consultation
    }

    // Navigate to extension payment when patient accepts extension (grace period starts)
    LaunchedEffect(sessionState.phase) {
        if (sessionState.phase == ConsultationPhase.GRACE_PERIOD && sessionState.consultationId.isNotBlank()) {
            onNavigateToExtensionPayment(
                sessionState.consultationId,
                sessionState.consultationFee,
                sessionState.serviceType,
            )
        }
    }

    ChatContent(
        messages = uiState.messages,
        isLoading = uiState.isLoading,
        currentUserId = uiState.currentUserId,
        otherPartyTyping = uiState.otherPartyTyping,
        consultationId = uiState.consultationId,
        onSendMessage = viewModel::sendMessage,
        onTypingChanged = viewModel::onTypingChanged,
        onBack = {
            if (!isActive) onBack()
        },
        modifier = modifier,
        error = uiState.error,
        sendError = uiState.sendError,
        timerContent = {
            if (!sessionState.isLoading) {
                ConsultationTimerBar(
                    phase = sessionState.phase,
                    remainingSeconds = sessionState.remainingSeconds,
                    extensionCount = sessionState.extensionCount,
                )
            }
        },
        bottomOverlay = {
            when (sessionState.phase) {
                ConsultationPhase.AWAITING_EXTENSION -> {
                    if (sessionState.extensionRequested && !sessionState.patientDeclined) {
                        PatientExtensionPrompt(
                            consultationFee = sessionState.consultationFee,
                            durationMinutes = sessionState.originalDurationMinutes,
                            onAccept = viewModel::acceptExtension,
                            onDecline = viewModel::declineExtension,
                        )
                    }
                }
                ConsultationPhase.GRACE_PERIOD -> {
                    GracePeriodBanner(remainingSeconds = sessionState.remainingSeconds)
                }
                else -> {}
            }
        },
    )
}

package com.esiri.esiriplus.feature.doctor.screen

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.ConsultationPhase
import com.esiri.esiriplus.feature.chat.ui.ChatContent
import com.esiri.esiriplus.feature.chat.ui.ConsultationTimerBar
import com.esiri.esiriplus.feature.chat.ui.DoctorExtensionOverlay
import com.esiri.esiriplus.feature.chat.ui.GracePeriodBanner
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorChatViewModel

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun DoctorConsultationDetailScreen(
    onStartVideoCall: (String) -> Unit,
    onWriteReport: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()

    // Block back navigation during active consultation
    val isActive = sessionState.phase != ConsultationPhase.COMPLETED
    BackHandler(enabled = isActive) {
        // Swallow back press — doctor must explicitly end consultation
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
        topBarActions = {
            IconButton(onClick = { onStartVideoCall(uiState.consultationId) }) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "Video Call",
                    tint = BrandTeal,
                )
            }
            IconButton(onClick = { onWriteReport(uiState.consultationId) }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Write Report",
                    tint = BrandTeal,
                )
            }
        },
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
                    DoctorExtensionOverlay(
                        extensionRequested = sessionState.extensionRequested,
                        patientDeclined = sessionState.patientDeclined,
                        onRequestExtension = viewModel::requestExtension,
                        onEndConsultation = viewModel::endConsultation,
                    )
                }
                ConsultationPhase.GRACE_PERIOD -> {
                    GracePeriodBanner(remainingSeconds = sessionState.remainingSeconds)
                }
                else -> {}
            }
        },
    )
}

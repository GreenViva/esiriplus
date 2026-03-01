package com.esiri.esiriplus.feature.doctor.screen

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    // Navigate back to dashboard when consultation ends
    LaunchedEffect(sessionState.phase) {
        if (sessionState.phase == ConsultationPhase.COMPLETED) {
            onBack()
        }
    }

    val isInputEnabled = sessionState.phase == ConsultationPhase.ACTIVE
    val canEnd = sessionState.phase == ConsultationPhase.ACTIVE ||
        sessionState.phase == ConsultationPhase.AWAITING_EXTENSION
    var showEndDialog by remember { mutableStateOf(false) }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("End Consultation", color = Color.Black) },
            text = { Text("Are you sure you want to end this consultation?", color = Color.Black) },
            confirmButton = {
                TextButton(onClick = {
                    showEndDialog = false
                    viewModel.endConsultation()
                }) {
                    Text("End", color = Color(0xFFDC2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) {
                    Text("Cancel", color = Color.Black)
                }
            },
        )
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
        isInputEnabled = isInputEnabled,
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
            if (canEnd) {
                IconButton(onClick = { showEndDialog = true }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "End Consultation",
                        tint = Color(0xFFDC2626),
                    )
                }
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
                        patientDeclined = sessionState.patientDeclined,
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

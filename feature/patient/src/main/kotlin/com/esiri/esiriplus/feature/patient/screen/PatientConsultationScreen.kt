package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.chat.ui.ChatContent
import com.esiri.esiriplus.feature.patient.viewmodel.PatientConsultationViewModel

@Composable
fun PatientConsultationScreen(
    onNavigateToPayment: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientConsultationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    ChatContent(
        messages = uiState.messages,
        isLoading = uiState.isLoading,
        currentUserId = uiState.currentUserId,
        otherPartyTyping = uiState.otherPartyTyping,
        consultationId = uiState.consultationId,
        onSendMessage = viewModel::sendMessage,
        onTypingChanged = viewModel::onTypingChanged,
        onBack = onBack,
        modifier = modifier,
        error = uiState.error,
    )
}

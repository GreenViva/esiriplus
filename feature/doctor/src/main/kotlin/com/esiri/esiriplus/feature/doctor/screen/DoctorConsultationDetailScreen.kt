package com.esiri.esiriplus.feature.doctor.screen

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
import com.esiri.esiriplus.feature.chat.ui.ChatContent
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
    )
}

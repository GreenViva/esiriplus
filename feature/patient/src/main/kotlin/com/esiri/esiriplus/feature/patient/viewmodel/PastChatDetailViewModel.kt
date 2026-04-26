package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.dao.MessageDao
import com.esiri.esiriplus.core.database.entity.MessageEntity
import com.esiri.esiriplus.feature.patient.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PastChatDetailUiState(
    val title: String = "Chat",
    val isFollowUp: Boolean = false,
    val messages: List<MessageEntity> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class PastChatDetailViewModel @Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
    private val consultationDao: ConsultationDao,
    private val doctorProfileDao: DoctorProfileDao,
    private val messageDao: MessageDao,
) : ViewModel() {

    private val consultationId: String = checkNotNull(savedStateHandle.get<String>(KEY)) {
        "PastChatDetailRoute requires consultationId"
    }

    private val headerTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PastChatDetailUiState> = combine(
        messageDao.getByConsultationId(consultationId),
        headerTrigger.flatMapLatest {
            kotlinx.coroutines.flow.flow {
                val consultation = consultationDao.getById(consultationId)
                val fallback = application.getString(R.string.past_chats_default_doctor)
                val doctorName = consultation?.doctorId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { doctorProfileDao.getById(it)?.fullName }
                    ?: fallback
                val isFollowUp = consultation?.parentConsultationId != null ||
                    consultation?.serviceType.equals("FOLLOW_UP", ignoreCase = true)
                val title = if (isFollowUp) {
                    application.getString(R.string.past_chats_followup_with, doctorName)
                } else {
                    application.getString(R.string.past_chats_chat_with, doctorName)
                }
                emit(title to isFollowUp)
            }
        },
    ) { messages, header ->
        PastChatDetailUiState(
            title = header.first,
            isFollowUp = header.second,
            messages = messages,
            isLoading = false,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PastChatDetailUiState(),
        )

    init {
        // Force a re-resolve in case the doctor profile or consultation row arrives after
        // the screen mounts.
        viewModelScope.launch { headerTrigger.value = headerTrigger.value + 1 }
    }

    companion object {
        const val KEY = "consultationId"
    }
}

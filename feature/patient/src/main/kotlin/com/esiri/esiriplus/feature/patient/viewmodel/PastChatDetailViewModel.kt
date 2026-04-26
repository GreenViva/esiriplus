package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.dao.MessageDao
import com.esiri.esiriplus.core.database.entity.MessageEntity
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
                val doctorName = consultation?.doctorId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { doctorProfileDao.getById(it)?.fullName }
                    ?: "Doctor"
                val isFollowUp = consultation?.parentConsultationId != null ||
                    consultation?.serviceType.equals("FOLLOW_UP", ignoreCase = true)
                val title = if (isFollowUp) "Follow-up with $doctorName" else "Chat with $doctorName"
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

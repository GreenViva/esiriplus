package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.domain.model.Consultation
import com.esiri.esiriplus.core.domain.model.ServiceType
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
import com.esiri.esiriplus.feature.patient.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI-shaped row for the Past chats list. */
data class PastChatItem(
    val consultation: Consultation,
    val doctorName: String,
    val isFollowUp: Boolean,
)

data class ConsultationHistoryUiState(
    val items: List<PastChatItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ConsultationHistoryViewModel @Inject constructor(
    private val application: Application,
    private val consultationRepository: ConsultationRepository,
    private val authRepository: AuthRepository,
    private val doctorProfileDao: DoctorProfileDao,
) : ViewModel() {

    private val _refreshTrigger = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ConsultationHistoryUiState> = combine(
        _refreshTrigger.flatMapLatest {
            authRepository.currentSession
                .flatMapLatest { session ->
                    val userId = session?.user?.id
                    if (userId == null) {
                        flowOf(
                            ConsultationHistoryUiState(
                                isLoading = false,
                                error = application.getString(R.string.vm_not_signed_in),
                            ),
                        )
                    } else {
                        consultationRepository.getConsultationsForPatient(userId)
                            .map { consultations -> consultations.toItems() }
                            .map { items ->
                                ConsultationHistoryUiState(
                                    items = items,
                                    isLoading = false,
                                )
                            }
                    }
                }
                .catch { e ->
                    emit(
                        ConsultationHistoryUiState(
                            isLoading = false,
                            error = e.message ?: application.getString(R.string.vm_failed_load_consultations),
                        ),
                    )
                }
        },
        _isRefreshing,
    ) { state, refreshing ->
        state.copy(isRefreshing = refreshing)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConsultationHistoryUiState(),
        )

    private suspend fun List<Consultation>.toItems(): List<PastChatItem> {
        val fallback = application.getString(R.string.past_chats_default_doctor)
        return map { c ->
            val name = c.doctorId
                ?.takeIf { it.isNotBlank() }
                ?.let { doctorProfileDao.getById(it)?.fullName }
                ?: fallback
            PastChatItem(
                consultation = c,
                doctorName = name,
                isFollowUp = c.serviceType == ServiceType.FOLLOW_UP,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshTrigger.value++
            kotlinx.coroutines.delay(500)
            _isRefreshing.value = false
        }
    }
}

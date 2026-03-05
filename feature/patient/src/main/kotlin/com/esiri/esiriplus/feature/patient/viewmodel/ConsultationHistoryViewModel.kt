package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.model.Consultation
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

data class ConsultationHistoryUiState(
    val consultations: List<Consultation> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

// TODO: Localize hardcoded user-facing strings (error messages).
//  Inject Application context and use context.getString(R.string.xxx) from feature.patient.R
@HiltViewModel
class ConsultationHistoryViewModel @Inject constructor(
    private val consultationRepository: ConsultationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ConsultationHistoryUiState> = authRepository.currentSession
        .flatMapLatest { session ->
            val userId = session?.user?.id
            if (userId == null) {
                flowOf(ConsultationHistoryUiState(isLoading = false, error = "Not signed in"))
            } else {
                consultationRepository.getConsultationsForPatient(userId)
                    .map { consultations ->
                        ConsultationHistoryUiState(
                            consultations = consultations,
                            isLoading = false,
                        )
                    }
            }
        }
        .catch { e ->
            emit(
                ConsultationHistoryUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load consultations",
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConsultationHistoryUiState(),
        )
}

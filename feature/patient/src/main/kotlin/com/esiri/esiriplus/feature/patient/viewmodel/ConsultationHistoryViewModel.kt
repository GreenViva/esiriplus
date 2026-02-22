package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.model.Consultation
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsultationHistoryUiState(
    val consultations: List<Consultation> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ConsultationHistoryViewModel @Inject constructor(
    private val consultationRepository: ConsultationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsultationHistoryUiState())
    val uiState: StateFlow<ConsultationHistoryUiState> = _uiState.asStateFlow()

    init {
        loadConsultations()
    }

    private fun loadConsultations() {
        viewModelScope.launch {
            val session = authRepository.currentSession.firstOrNull()
            val userId = session?.user?.id
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }

            consultationRepository.getConsultationsForPatient(userId)
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to load consultations")
                    }
                }
                .collect { consultations ->
                    _uiState.update {
                        it.copy(consultations = consultations, isLoading = false, error = null)
                    }
                }
        }
    }
}

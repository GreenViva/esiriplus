package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.model.PatientReport
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.PatientReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportsUiState(
    val reports: List<PatientReport> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val patientReportRepository: PatientReportRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReports()
    }

    private fun loadReports() {
        viewModelScope.launch {
            val session = authRepository.currentSession.firstOrNull()
            val userId = session?.user?.id
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }

            patientReportRepository.getReportsByPatientSession(userId)
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to load reports")
                    }
                }
                .collect { reports ->
                    _uiState.update {
                        it.copy(reports = reports, isLoading = false, error = null)
                    }
                }
        }
    }
}

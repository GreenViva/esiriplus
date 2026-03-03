package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
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
    val isRefreshing: Boolean = false,
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
            if (session == null) {
                _uiState.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }

            // Fetch from server — this is the source of truth for patients
            try {
                val serverReports = patientReportRepository.fetchReportsFromServer()
                _uiState.update {
                    it.copy(
                        reports = serverReports,
                        isLoading = false,
                        error = null,
                    )
                }
                // Try to cache locally (may fail due to FK constraints — non-fatal)
                tryCacheReports(serverReports)
            } catch (e: Exception) {
                Log.w(TAG, "Server fetch failed: ${e.message}")
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load reports")
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
                val serverReports = patientReportRepository.fetchReportsFromServer()
                _uiState.update {
                    it.copy(
                        reports = serverReports,
                        isRefreshing = false,
                        error = null,
                    )
                }
                tryCacheReports(serverReports)
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed: ${e.message}")
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun tryCacheReports(reports: List<PatientReport>) {
        try {
            if (reports.isNotEmpty()) {
                patientReportRepository.saveReports(reports)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Local cache save failed (FK constraint?): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ReportsVM"
    }
}

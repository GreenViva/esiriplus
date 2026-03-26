package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.model.PatientReport
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.PatientReportRepository
import com.esiri.esiriplus.feature.patient.R
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
    val hasUnread: Boolean = false,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val application: Application,
    private val patientReportRepository: PatientReportRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReports()
    }

    private fun getReadReportIds(): Set<String> =
        prefs.getStringSet(KEY_READ_REPORT_IDS, emptySet()) ?: emptySet()

    private fun applyReadStatus(reports: List<PatientReport>): List<PatientReport> {
        val readIds = getReadReportIds()
        return reports.map { it.copy(isRead = it.reportId in readIds) }
    }

    fun markAsRead(reportId: String) {
        val readIds = getReadReportIds().toMutableSet()
        readIds.add(reportId)
        prefs.edit().putStringSet(KEY_READ_REPORT_IDS, readIds).apply()
        _uiState.update { state ->
            state.copy(
                reports = state.reports.map {
                    if (it.reportId == reportId) it.copy(isRead = true) else it
                },
                hasUnread = state.reports.any { it.reportId != reportId && !it.isRead },
            )
        }
    }

    private fun loadReports() {
        viewModelScope.launch {
            val session = authRepository.currentSession.firstOrNull()
            if (session == null) {
                _uiState.update { it.copy(isLoading = false, error = application.getString(R.string.vm_not_signed_in)) }
                return@launch
            }

            // Fetch from server — this is the source of truth for patients
            try {
                val serverReports = patientReportRepository.fetchReportsFromServer()
                val withReadStatus = applyReadStatus(serverReports)
                _uiState.update {
                    it.copy(
                        reports = withReadStatus,
                        isLoading = false,
                        error = null,
                        hasUnread = withReadStatus.any { r -> !r.isRead },
                    )
                }
                // Try to cache locally (may fail due to FK constraints — non-fatal)
                tryCacheReports(serverReports)
            } catch (e: Exception) {
                Log.w(TAG, "Server fetch failed: ${e.message}")
                _uiState.update {
                    it.copy(isLoading = false, error = application.getString(R.string.vm_failed_load_reports))
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
                val serverReports = patientReportRepository.fetchReportsFromServer()
                val withReadStatus = applyReadStatus(serverReports)
                _uiState.update {
                    it.copy(
                        reports = withReadStatus,
                        isRefreshing = false,
                        error = null,
                        hasUnread = withReadStatus.any { r -> !r.isRead },
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
        private const val PREFS_NAME = "report_read_prefs"
        private const val KEY_READ_REPORT_IDS = "read_report_ids"
    }
}

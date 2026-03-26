package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.model.PatientReport
import com.esiri.esiriplus.core.domain.repository.PatientReportRepository
import com.esiri.esiriplus.feature.patient.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportDetailUiState(
    val report: PatientReport? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
    private val patientReportRepository: PatientReportRepository,
) : ViewModel() {

    private val reportId: String = savedStateHandle["reportId"] ?: ""

    private val _uiState = MutableStateFlow(ReportDetailUiState())
    val uiState: StateFlow<ReportDetailUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        markAsRead()
        loadReport()
    }

    private fun markAsRead() {
        if (reportId.isBlank()) return
        val readIds = (prefs.getStringSet(KEY_READ_REPORT_IDS, emptySet()) ?: emptySet()).toMutableSet()
        readIds.add(reportId)
        prefs.edit().putStringSet(KEY_READ_REPORT_IDS, readIds).apply()
    }

    private fun loadReport() {
        viewModelScope.launch {
            // Try local Room first
            val localReport = patientReportRepository.observeReportById(reportId).firstOrNull()
            if (localReport != null) {
                _uiState.update { it.copy(report = localReport, isLoading = false) }
                return@launch
            }

            // Fallback: fetch from server and find by reportId
            try {
                val serverReports = patientReportRepository.fetchReportsFromServer()
                val report = serverReports.find { it.reportId == reportId }
                _uiState.update {
                    it.copy(
                        report = report,
                        isLoading = false,
                        error = if (report == null) application.getString(R.string.vm_report_not_found) else null,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch report from server: ${e.message}")
                _uiState.update {
                    it.copy(isLoading = false, error = application.getString(R.string.vm_failed_load_report))
                }
            }
        }
    }

    companion object {
        private const val TAG = "ReportDetailVM"
        private const val PREFS_NAME = "report_read_prefs"
        private const val KEY_READ_REPORT_IDS = "read_report_ids"
    }
}

package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

data class DoctorReportUiState(
    val consultationId: String = "",
    val serviceType: String = "",
    val chiefComplaint: String = "",

    // Report fields
    val diagnosis: String = "",
    val prescription: String = "",
    val additionalNotes: String = "",

    // AI-generated report sections
    val aiReport: AiReportContent? = null,
    val isGeneratingReport: Boolean = false,

    // Submit state
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = true,
)

data class AiReportContent(
    val chiefComplaint: String,
    val history: String,
    val assessment: String,
    val plan: String,
    val followUp: String,
)

@Serializable
private data class GenerateReportResponse(
    val message: String,
    @SerialName("report_id") val reportId: String? = null,
    @SerialName("verification_code") val verificationCode: String? = null,
    val report: ReportContentDto? = null,
    @SerialName("report_url") val reportUrl: String? = null,
)

@Serializable
private data class ReportContentDto(
    val chief_complaint: String = "",
    val history: String = "",
    val assessment: String = "",
    val plan: String = "",
    val follow_up: String = "",
)

@HiltViewModel
class DoctorReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val consultationDao: ConsultationDao,
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val consultationId: String = savedStateHandle["consultationId"] ?: ""

    private val _uiState = MutableStateFlow(DoctorReportUiState(consultationId = consultationId))
    val uiState: StateFlow<DoctorReportUiState> = _uiState.asStateFlow()

    init {
        loadConsultationInfo()
    }

    private fun loadConsultationInfo() {
        viewModelScope.launch {
            val consultation = consultationDao.getById(consultationId)
            if (consultation != null) {
                _uiState.update {
                    it.copy(
                        serviceType = consultation.serviceType,
                        isLoading = false,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateDiagnosis(value: String) {
        _uiState.update { it.copy(diagnosis = value, errorMessage = null) }
    }

    fun updatePrescription(value: String) {
        _uiState.update { it.copy(prescription = value, errorMessage = null) }
    }

    fun updateAdditionalNotes(value: String) {
        _uiState.update { it.copy(additionalNotes = value, errorMessage = null) }
    }

    fun generateAiReport() {
        if (_uiState.value.isGeneratingReport) return

        _uiState.update { it.copy(isGeneratingReport = true, errorMessage = null) }

        viewModelScope.launch {
            val body = buildJsonObject {
                put("consultation_id", JsonPrimitive(consultationId))
                val notes = _uiState.value.additionalNotes.trim()
                if (notes.isNotBlank()) {
                    put("additional_notes", JsonPrimitive(notes))
                }
            }

            when (val result = edgeFunctionClient.invokeAndDecode<GenerateReportResponse>(
                "generate-consultation-report",
                body,
            )) {
                is ApiResult.Success -> {
                    val report = result.data.report
                    if (report != null) {
                        _uiState.update {
                            it.copy(
                                aiReport = AiReportContent(
                                    chiefComplaint = report.chief_complaint,
                                    history = report.history,
                                    assessment = report.assessment,
                                    plan = report.plan,
                                    followUp = report.follow_up,
                                ),
                                diagnosis = report.assessment.ifBlank { it.diagnosis },
                                isGeneratingReport = false,
                            )
                        }
                    } else {
                        // Report already exists
                        _uiState.update {
                            it.copy(
                                isGeneratingReport = false,
                                submitSuccess = true,
                            )
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "AI report generation failed: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isGeneratingReport = false,
                            errorMessage = result.message ?: "Failed to generate AI report",
                        )
                    }
                }
                else -> { /* no-op */ }
            }
        }
    }

    fun submitReport() {
        val state = _uiState.value
        if (state.diagnosis.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a diagnosis or generate an AI report") }
            return
        }
        if (state.isSubmitting) return

        // If AI report was already generated, just mark as complete
        if (state.aiReport != null) {
            _uiState.update { it.copy(submitSuccess = true) }
            return
        }

        // Generate AI report with the manual notes as additional context
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val notes = buildString {
                append("Diagnosis: ${state.diagnosis}")
                if (state.prescription.isNotBlank()) append("\nPrescription: ${state.prescription}")
                if (state.additionalNotes.isNotBlank()) append("\nNotes: ${state.additionalNotes}")
            }

            val body = buildJsonObject {
                put("consultation_id", JsonPrimitive(consultationId))
                put("additional_notes", JsonPrimitive(notes))
            }

            when (val result = edgeFunctionClient.invoke("generate-consultation-report", body)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, submitSuccess = true)
                    }
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Report submission failed: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message ?: "Failed to submit report",
                        )
                    }
                }
                else -> { /* no-op */ }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val TAG = "DoctorReportVM"
    }
}

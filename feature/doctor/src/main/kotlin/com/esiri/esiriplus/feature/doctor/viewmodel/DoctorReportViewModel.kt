package com.esiri.esiriplus.feature.doctor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.feature.doctor.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

data class DoctorReportUiState(
    val consultationId: String = "",
    val serviceType: String = "",

    // Form fields matching wireframes
    val diagnosedProblem: String = "",
    val category: String = "",
    val otherCategory: String = "",
    val severity: String = "Mild",
    val treatmentPlan: String = "",
    val furtherNotes: String = "",
    val followUpRecommended: Boolean = false,

    // UI state
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class DoctorReportViewModel @Inject constructor(
    private val application: Application,
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

    fun updateDiagnosedProblem(value: String) {
        _uiState.update { it.copy(diagnosedProblem = value, errorMessage = null) }
    }

    fun updateCategory(value: String) {
        _uiState.update { it.copy(category = value, errorMessage = null) }
    }

    fun updateOtherCategory(value: String) {
        _uiState.update { it.copy(otherCategory = value, errorMessage = null) }
    }

    fun updateSeverity(value: String) {
        _uiState.update { it.copy(severity = value, errorMessage = null) }
    }

    fun updateTreatmentPlan(value: String) {
        _uiState.update { it.copy(treatmentPlan = value, errorMessage = null) }
    }

    fun updateFurtherNotes(value: String) {
        _uiState.update { it.copy(furtherNotes = value, errorMessage = null) }
    }

    fun toggleFollowUp() {
        _uiState.update { it.copy(followUpRecommended = !it.followUpRecommended, errorMessage = null) }
    }

    fun submitReport() {
        val state = _uiState.value
        if (state.diagnosedProblem.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_diagnosed_problem_required)) }
            return
        }
        if (state.category.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_category_required)) }
            return
        }
        if (state.category == "Other" && state.otherCategory.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_specify_category)) }
            return
        }
        if (state.treatmentPlan.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_treatment_plan_required)) }
            return
        }
        if (state.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        val effectiveCategory = if (state.category == "Other") state.otherCategory else state.category

        viewModelScope.launch {
            val body = buildJsonObject {
                put("consultation_id", JsonPrimitive(consultationId))
                put("diagnosed_problem", JsonPrimitive(state.diagnosedProblem.trim()))
                put("category", JsonPrimitive(effectiveCategory.trim()))
                put("severity", JsonPrimitive(state.severity))
                put("treatment_plan", JsonPrimitive(state.treatmentPlan.trim()))
                put("further_notes", JsonPrimitive(state.furtherNotes.trim()))
                put("follow_up_recommended", JsonPrimitive(state.followUpRecommended))
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
                            errorMessage = result.message,
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Report submission network error: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message,
                        )
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = application.getString(R.string.vm_session_expired),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DoctorReportVM"
        val CATEGORIES = listOf(
            "General Medicine",
            "Neurological Conditions",
            "Cardiovascular",
            "Respiratory",
            "Gastrointestinal",
            "Musculoskeletal",
            "Dermatological",
            "Mental Health",
            "Infectious Disease",
            "Other",
        )
        val SEVERITIES = listOf("Mild", "Moderate", "Severe")
    }
}

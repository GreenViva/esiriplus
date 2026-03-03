package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

data class SummarySection(val title: String, val content: String)

data class PatientSummaryUiState(
    val isGenerating: Boolean = false,
    val error: String? = null,
    val patientName: String = "",
    val sections: List<SummarySection> = emptyList(),
    val showSummary: Boolean = false,
)

@HiltViewModel
class PatientSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val consultationId: String = savedStateHandle["consultationId"] ?: ""

    private val _uiState = MutableStateFlow(PatientSummaryUiState())
    val uiState: StateFlow<PatientSummaryUiState> = _uiState.asStateFlow()

    fun generateSummary() {
        if (consultationId.isBlank()) {
            _uiState.update { it.copy(error = "No consultation ID") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, showSummary = false) }

            val body = buildJsonObject { put("consultation_id", consultationId) }

            when (val result = edgeFunctionClient.invoke("generate-patient-summary", body)) {
                is ApiResult.Success -> {
                    try {
                        val response = json.parseToJsonElement(result.data).jsonObject
                        val patientName = response["patient_name"]?.jsonPrimitive?.content ?: "Patient"
                        val summary = response["summary"]?.jsonObject

                        val sections = buildList {
                            summary?.get("patient_overview")?.jsonPrimitive?.content?.let {
                                add(SummarySection("Patient Overview", it))
                            }
                            summary?.get("medical_history")?.jsonPrimitive?.content?.let {
                                add(SummarySection("Medical History", it))
                            }
                            summary?.get("current_conditions")?.jsonPrimitive?.content?.let {
                                add(SummarySection("Current Conditions", it))
                            }
                            summary?.get("treatment_summary")?.jsonPrimitive?.content?.let {
                                add(SummarySection("Treatment Summary", it))
                            }
                            summary?.get("vital_signs_summary")?.jsonPrimitive?.content?.let {
                                add(SummarySection("Vital Signs", it))
                            }
                            summary?.get("recommendations")?.jsonPrimitive?.content?.let {
                                add(SummarySection("Recommendations", it))
                            }
                        }

                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                patientName = patientName,
                                sections = sections,
                                showSummary = true,
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse patient summary", e)
                        _uiState.update { it.copy(isGenerating = false, error = "Failed to parse summary") }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isGenerating = false, error = result.message) }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isGenerating = false, error = "Network error: ${result.message}") }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(isGenerating = false, error = "Unauthorized") }
                }
            }
        }
    }

    fun dismissSummary() {
        _uiState.update { it.copy(showSummary = false, sections = emptyList()) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val TAG = "PatientSummaryVM"
    }
}

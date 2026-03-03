package com.esiri.esiriplus.feature.admin.viewmodel

import android.util.Log
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

data class ReportSection(val title: String, val content: String)

data class AdminReportUiState(
    val isGenerating: Boolean = false,
    val error: String? = null,
    val reportTitle: String = "",
    val reportSubtitle: String = "",
    val sections: List<ReportSection> = emptyList(),
    val showReport: Boolean = false,
)

@HiltViewModel
class AdminReportViewModel @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _uiState = MutableStateFlow(AdminReportUiState())
    val uiState: StateFlow<AdminReportUiState> = _uiState.asStateFlow()

    fun generatePerformanceReport(doctorId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, showReport = false) }

            val body = buildJsonObject { put("doctor_id", doctorId) }

            when (val result = edgeFunctionClient.invoke("generate-doctor-performance-report", body)) {
                is ApiResult.Success -> {
                    try {
                        val response = json.parseToJsonElement(result.data).jsonObject
                        val doctorName = response["doctor_name"]?.jsonPrimitive?.content ?: "Doctor"
                        val report = response["report"]?.jsonObject

                        val sections = buildList {
                            report?.get("executive_summary")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Executive Summary", it))
                            }
                            report?.get("consultation_analysis")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Consultation Analysis", it))
                            }
                            report?.get("patient_satisfaction")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Patient Satisfaction", it))
                            }
                            report?.get("financial_overview")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Financial Overview", it))
                            }
                            report?.get("recommendations")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Recommendations", it))
                            }
                            report?.get("risk_flags")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Risk Flags", it))
                            }
                        }

                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                reportTitle = "Performance Report",
                                reportSubtitle = doctorName,
                                sections = sections,
                                showReport = true,
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse performance report", e)
                        _uiState.update { it.copy(isGenerating = false, error = "Failed to parse report") }
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

    fun generateAnalyticsReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, showReport = false) }

            val body = buildJsonObject {}

            when (val result = edgeFunctionClient.invoke("generate-analytics-report", body)) {
                is ApiResult.Success -> {
                    try {
                        val response = json.parseToJsonElement(result.data).jsonObject
                        val report = response["report"]?.jsonObject

                        val sections = buildList {
                            report?.get("executive_summary")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Executive Summary", it))
                            }
                            report?.get("doctor_workforce")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Doctor Workforce", it))
                            }
                            report?.get("consultation_performance")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Consultation Performance", it))
                            }
                            report?.get("revenue_analysis")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Revenue Analysis", it))
                            }
                            report?.get("patient_satisfaction")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Patient Satisfaction", it))
                            }
                            report?.get("growth_opportunities")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Growth Opportunities", it))
                            }
                            report?.get("risk_areas")?.jsonPrimitive?.content?.let {
                                add(ReportSection("Risk Areas", it))
                            }
                        }

                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                reportTitle = "Platform Analytics Report",
                                reportSubtitle = "eSIRI Plus",
                                sections = sections,
                                showReport = true,
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse analytics report", e)
                        _uiState.update { it.copy(isGenerating = false, error = "Failed to parse report") }
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

    fun dismissReport() {
        _uiState.update { it.copy(showReport = false, sections = emptyList()) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val TAG = "AdminReportVM"
    }
}

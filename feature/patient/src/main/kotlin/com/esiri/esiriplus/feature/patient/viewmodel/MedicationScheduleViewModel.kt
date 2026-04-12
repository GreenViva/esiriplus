package com.esiri.esiriplus.feature.patient.viewmodel

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@Serializable
data class MedicationTimetableDto(
    @SerialName("timetable_id") val timetableId: String,
    @SerialName("medication_name") val medicationName: String,
    val dosage: String? = null,
    val form: String? = null,
    @SerialName("times_per_day") val timesPerDay: Int = 1,
    @SerialName("scheduled_times") val scheduledTimes: List<String> = emptyList(),
    @SerialName("duration_days") val durationDays: Int = 1,
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
)

data class MedicationScheduleUiState(
    val timetables: List<MedicationTimetableDto> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class MedicationScheduleViewModel @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicationScheduleUiState())
    val uiState: StateFlow<MedicationScheduleUiState> = _uiState.asStateFlow()

    init {
        loadSchedules()
    }

    fun loadSchedules() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Query medication_timetables via PostgREST (through edge function or direct)
            // For now, use a simple approach: query via the Supabase PostgREST auto-API
            // The RLS policy allows patients to see their own timetables.
            val body = buildJsonObject {
                put("action", "get_schedules")
            }
            when (val result = edgeFunctionClient.invokeAndDecode<MedicationScheduleResponse>(
                functionName = "medication-reminder-callback",
                body = body,
                patientAuth = true,
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(timetables = result.data.timetables, isLoading = false)
                    }
                }
                else -> {
                    Log.w(TAG, "Failed to load medication schedules")
                    _uiState.update { it.copy(isLoading = false, timetables = emptyList()) }
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val TAG = "MedScheduleVM"
    }
}

@Serializable
data class MedicationScheduleResponse(
    val timetables: List<MedicationTimetableDto> = emptyList(),
)

package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.dao.DoctorAvailabilityDao
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject

@Serializable
data class DayScheduleDto(
    val enabled: Boolean = false,
    val start: String = "",
    val end: String = "",
)

data class BookAppointmentUiState(
    val doctorName: String = "",
    val specialty: String = "",
    val averageRating: Double = 0.0,
    val totalRatings: Int = 0,
    val isVerified: Boolean = false,
    val availableSlots: Int = -1, // -1 = loading
    val availabilitySchedule: Map<String, DayScheduleDto> = emptyMap(),
    val chiefComplaint: String = "",
    val preferredLanguage: String = "en",
    val isSubmitting: Boolean = false,
    val bookingSuccess: String? = null, // consultation ID on success
    val errorMessage: String? = null,
    val isLoadingDoctor: Boolean = true,
)

private val categoryToSpecialty = mapOf(
    "NURSE" to "nurse",
    "CLINICAL_OFFICER" to "clinical_officer",
    "PHARMACIST" to "pharmacist",
    "GP" to "gp",
    "SPECIALIST" to "specialist",
    "PSYCHOLOGIST" to "psychologist",
)

@HiltViewModel
class BookAppointmentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val consultationRepository: ConsultationRepository,
    private val edgeFunctionClient: EdgeFunctionClient,
    private val doctorProfileDao: DoctorProfileDao,
    private val doctorAvailabilityDao: DoctorAvailabilityDao,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val doctorId: String = savedStateHandle["doctorId"] ?: ""
    private val serviceCategory: String = savedStateHandle["serviceCategory"] ?: ""
    private val serviceType: String = categoryToSpecialty[serviceCategory] ?: serviceCategory

    private val _uiState = MutableStateFlow(BookAppointmentUiState())
    val uiState: StateFlow<BookAppointmentUiState> = _uiState.asStateFlow()

    init {
        loadDoctorInfo()
        loadSlotAvailability()
        loadAvailabilitySchedule()
    }

    private fun loadDoctorInfo() {
        viewModelScope.launch {
            val doctor = doctorProfileDao.getById(doctorId)
            if (doctor != null) {
                _uiState.update {
                    it.copy(
                        doctorName = doctor.fullName,
                        specialty = specialtyDisplayNames[doctor.specialty] ?: doctor.specialty,
                        averageRating = doctor.averageRating,
                        totalRatings = doctor.totalRatings,
                        isVerified = doctor.isVerified,
                        isLoadingDoctor = false,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingDoctor = false) }
            }
        }
    }

    private fun loadSlotAvailability() {
        viewModelScope.launch {
            val body = buildJsonObject {
                putJsonArray("doctor_ids") { add(JsonPrimitive(doctorId)) }
            }
            when (val result = edgeFunctionClient.invoke("get-doctor-slots", body)) {
                is ApiResult.Success -> {
                    try {
                        val response = json.decodeFromString<GetDoctorSlotsResponse>(result.data)
                        val slot = response.slots[doctorId]
                        _uiState.update {
                            it.copy(availableSlots = slot?.available ?: 10)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse slot response", e)
                        _uiState.update { it.copy(availableSlots = 10) }
                    }
                }
                else -> {
                    Log.w(TAG, "Failed to fetch slots: $result")
                    _uiState.update { it.copy(availableSlots = 10) }
                }
            }
        }
    }

    private fun loadAvailabilitySchedule() {
        viewModelScope.launch {
            doctorAvailabilityDao.getByDoctorId(doctorId).collect { entity ->
                if (entity != null && entity.availabilitySchedule.isNotBlank()) {
                    try {
                        val schedule = json.decodeFromString<Map<String, DayScheduleDto>>(
                            entity.availabilitySchedule,
                        )
                        _uiState.update { it.copy(availabilitySchedule = schedule) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse availability schedule", e)
                    }
                }
            }
        }
    }

    fun updateChiefComplaint(value: String) {
        _uiState.update { it.copy(chiefComplaint = value, errorMessage = null) }
    }

    fun updatePreferredLanguage(lang: String) {
        _uiState.update { it.copy(preferredLanguage = lang) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun bookAppointment() {
        val state = _uiState.value
        if (state.chiefComplaint.trim().length < 10) {
            _uiState.update { it.copy(errorMessage = "Please describe your complaint (at least 10 characters)") }
            return
        }
        if (state.availableSlots == 0) {
            _uiState.update { it.copy(errorMessage = "Doctor has no available slots") }
            return
        }
        if (state.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val result = consultationRepository.bookAppointment(
                doctorId = doctorId,
                serviceType = serviceType,
                consultationType = "both",
                chiefComplaint = state.chiefComplaint.trim(),
                preferredLanguage = state.preferredLanguage,
            )

            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            bookingSuccess = result.data.id,
                            availableSlots = if (it.availableSlots > 0) it.availableSlots - 1 else 0,
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message ?: result.exception.message
                                ?: "Failed to book appointment",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    companion object {
        private const val TAG = "BookAppointmentVM"
    }
}

@Serializable
private data class GetDoctorSlotsResponse(
    val slots: Map<String, BookingSlotInfo> = emptyMap(),
)

@Serializable
private data class BookingSlotInfo(
    val used: Int = 0,
    val available: Int = 10,
    val total: Int = 10,
)

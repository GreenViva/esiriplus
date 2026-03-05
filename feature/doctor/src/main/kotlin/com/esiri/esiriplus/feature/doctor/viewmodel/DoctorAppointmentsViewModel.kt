package com.esiri.esiriplus.feature.doctor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Appointment
import com.esiri.esiriplus.core.domain.model.AppointmentStatus
import com.esiri.esiriplus.core.domain.repository.AppointmentRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.feature.doctor.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

enum class DoctorAppointmentTab { TODAY, UPCOMING, MISSED }

data class DoctorAppointmentsUiState(
    val selectedTab: DoctorAppointmentTab = DoctorAppointmentTab.TODAY,
    val todayAppointments: List<Appointment> = emptyList(),
    val upcomingAppointments: List<Appointment> = emptyList(),
    val missedAppointments: List<Appointment> = emptyList(),
    val isLoading: Boolean = true,
    val isRescheduling: String? = null,
    val isStartingSession: String? = null,
    val errorMessage: String? = null,
    // Reschedule dialog state
    val showRescheduleDialog: Boolean = false,
    val rescheduleAppointmentId: String? = null,
    val rescheduleNewTime: String = "",
    val rescheduleReason: String = "",
)

@Serializable
private data class StartSessionResponse(
    @SerialName("consultation_id") val consultationId: String? = null,
    val message: String? = null,
)

@HiltViewModel
class DoctorAppointmentsViewModel @Inject constructor(
    private val application: Application,
    private val appointmentRepository: AppointmentRepository,
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorAppointmentsUiState())
    val uiState: StateFlow<DoctorAppointmentsUiState> = _uiState.asStateFlow()

    /** Emits consultation ID when session starts successfully */
    private val _sessionStarted = MutableSharedFlow<String>()
    val sessionStarted: SharedFlow<String> = _sessionStarted.asSharedFlow()

    init {
        loadAppointments()
    }

    fun selectTab(tab: DoctorAppointmentTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadAppointments() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = appointmentRepository.getAppointments(limit = 100)) {
                is Result.Success -> {
                    val now = System.currentTimeMillis()
                    val todayStart = now - (now % (24 * 60 * 60 * 1000))
                    val todayEnd = todayStart + (24 * 60 * 60 * 1000)

                    val today = result.data.filter { apt ->
                        apt.scheduledAt in todayStart until todayEnd &&
                            apt.status in listOf(
                                AppointmentStatus.BOOKED,
                                AppointmentStatus.CONFIRMED,
                                AppointmentStatus.IN_PROGRESS,
                            )
                    }.sortedBy { it.scheduledAt }

                    val upcoming = result.data.filter { apt ->
                        apt.scheduledAt >= todayEnd &&
                            apt.status in listOf(
                                AppointmentStatus.BOOKED,
                                AppointmentStatus.CONFIRMED,
                            )
                    }.sortedBy { it.scheduledAt }

                    val missed = result.data.filter { apt ->
                        apt.status == AppointmentStatus.MISSED
                    }.sortedByDescending { it.scheduledAt }

                    _uiState.update {
                        it.copy(
                            todayAppointments = today,
                            upcomingAppointments = upcoming,
                            missedAppointments = missed,
                            isLoading = false,
                        )
                    }
                }
                is Result.Error -> {
                    Log.w(TAG, "Failed to load appointments: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message ?: application.getString(R.string.vm_failed_load_appointments),
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun showRescheduleDialog(appointmentId: String) {
        _uiState.update {
            it.copy(
                showRescheduleDialog = true,
                rescheduleAppointmentId = appointmentId,
                rescheduleNewTime = "",
                rescheduleReason = "",
            )
        }
    }

    fun dismissRescheduleDialog() {
        _uiState.update {
            it.copy(
                showRescheduleDialog = false,
                rescheduleAppointmentId = null,
            )
        }
    }

    fun updateRescheduleTime(time: String) {
        _uiState.update { it.copy(rescheduleNewTime = time) }
    }

    fun updateRescheduleReason(reason: String) {
        _uiState.update { it.copy(rescheduleReason = reason) }
    }

    fun rescheduleAppointment() {
        val state = _uiState.value
        val appointmentId = state.rescheduleAppointmentId ?: return
        if (state.rescheduleNewTime.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_enter_new_time)) }
            return
        }

        _uiState.update { it.copy(isRescheduling = appointmentId, showRescheduleDialog = false) }

        viewModelScope.launch {
            when (val result = appointmentRepository.rescheduleAppointment(
                appointmentId = appointmentId,
                newScheduledAt = state.rescheduleNewTime,
                reason = state.rescheduleReason,
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isRescheduling = null, rescheduleAppointmentId = null) }
                    loadAppointments()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isRescheduling = null,
                            errorMessage = result.message ?: application.getString(R.string.vm_failed_reschedule),
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun startSession(appointment: Appointment) {
        if (_uiState.value.isStartingSession != null) return

        _uiState.update { it.copy(isStartingSession = appointment.appointmentId, errorMessage = null) }

        viewModelScope.launch {
            // If the appointment already has a consultationId, navigate directly
            val existingConsultationId = appointment.consultationId
            if (!existingConsultationId.isNullOrBlank()) {
                _uiState.update { it.copy(isStartingSession = null) }
                _sessionStarted.emit(existingConsultationId)
                return@launch
            }

            // Create consultation from appointment via edge function
            val body = buildJsonObject {
                put("appointment_id", JsonPrimitive(appointment.appointmentId))
                put("service_type", JsonPrimitive(appointment.serviceType))
                put("consultation_type", JsonPrimitive(appointment.consultationType))
                put("chief_complaint", JsonPrimitive(appointment.chiefComplaint.ifBlank { "Scheduled appointment" }))
                put("doctor_id", JsonPrimitive(appointment.doctorId))
            }

            when (val result = edgeFunctionClient.invokeAndDecode<StartSessionResponse>(
                "create-consultation",
                body,
            )) {
                is ApiResult.Success -> {
                    val consultationId = result.data.consultationId
                    _uiState.update { it.copy(isStartingSession = null) }
                    if (consultationId != null) {
                        _sessionStarted.emit(consultationId)
                    } else {
                        _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_failed_start_session)) }
                    }
                    loadAppointments()
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Failed to start session: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isStartingSession = null,
                            errorMessage = result.message ?: application.getString(R.string.vm_failed_start_session),
                        )
                    }
                }
                is ApiResult.Unauthorized -> {
                    Log.w(TAG, "Unauthorized when starting session")
                    _uiState.update {
                        it.copy(
                            isStartingSession = null,
                            errorMessage = application.getString(R.string.vm_session_expired),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Network error starting session", result.exception)
                    _uiState.update {
                        it.copy(
                            isStartingSession = null,
                            errorMessage = application.getString(R.string.vm_network_error),
                        )
                    }
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val TAG = "DoctorAppointmentsVM"
    }
}

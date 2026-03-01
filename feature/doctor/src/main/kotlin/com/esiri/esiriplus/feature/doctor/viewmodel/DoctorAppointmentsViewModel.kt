package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Appointment
import com.esiri.esiriplus.core.domain.model.AppointmentStatus
import com.esiri.esiriplus.core.domain.repository.AppointmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DoctorAppointmentTab { TODAY, UPCOMING, MISSED }

data class DoctorAppointmentsUiState(
    val selectedTab: DoctorAppointmentTab = DoctorAppointmentTab.TODAY,
    val todayAppointments: List<Appointment> = emptyList(),
    val upcomingAppointments: List<Appointment> = emptyList(),
    val missedAppointments: List<Appointment> = emptyList(),
    val isLoading: Boolean = true,
    val isRescheduling: String? = null,
    val errorMessage: String? = null,
    // Reschedule dialog state
    val showRescheduleDialog: Boolean = false,
    val rescheduleAppointmentId: String? = null,
    val rescheduleNewTime: String = "",
    val rescheduleReason: String = "",
)

@HiltViewModel
class DoctorAppointmentsViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorAppointmentsUiState())
    val uiState: StateFlow<DoctorAppointmentsUiState> = _uiState.asStateFlow()

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
                            errorMessage = result.message ?: "Failed to load appointments",
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
            _uiState.update { it.copy(errorMessage = "Please enter the new time") }
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
                            errorMessage = result.message ?: "Failed to reschedule",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
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

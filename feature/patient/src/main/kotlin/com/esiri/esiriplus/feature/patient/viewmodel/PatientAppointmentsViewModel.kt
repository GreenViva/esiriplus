package com.esiri.esiriplus.feature.patient.viewmodel

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

enum class AppointmentTab { UPCOMING, PAST }

data class PatientAppointmentsUiState(
    val selectedTab: AppointmentTab = AppointmentTab.UPCOMING,
    val upcomingAppointments: List<Appointment> = emptyList(),
    val pastAppointments: List<Appointment> = emptyList(),
    val isLoading: Boolean = true,
    val isCancelling: String? = null, // appointment ID being cancelled
    val errorMessage: String? = null,
)

@HiltViewModel
class PatientAppointmentsViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientAppointmentsUiState())
    val uiState: StateFlow<PatientAppointmentsUiState> = _uiState.asStateFlow()

    init {
        loadAppointments()
    }

    fun selectTab(tab: AppointmentTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadAppointments() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = appointmentRepository.getAppointments(limit = 100)) {
                is Result.Success -> {
                    val now = System.currentTimeMillis()
                    val upcoming = result.data.filter { apt ->
                        apt.status in listOf(
                            AppointmentStatus.BOOKED,
                            AppointmentStatus.CONFIRMED,
                            AppointmentStatus.IN_PROGRESS,
                        )
                    }.sortedBy { it.scheduledAt }

                    val past = result.data.filter { apt ->
                        apt.status in listOf(
                            AppointmentStatus.COMPLETED,
                            AppointmentStatus.MISSED,
                            AppointmentStatus.CANCELLED,
                            AppointmentStatus.RESCHEDULED,
                        )
                    }.sortedByDescending { it.scheduledAt }

                    _uiState.update {
                        it.copy(
                            upcomingAppointments = upcoming,
                            pastAppointments = past,
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

    fun cancelAppointment(appointmentId: String) {
        _uiState.update { it.copy(isCancelling = appointmentId) }
        viewModelScope.launch {
            when (val result = appointmentRepository.cancelAppointment(appointmentId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isCancelling = null) }
                    loadAppointments()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isCancelling = null,
                            errorMessage = result.message ?: "Failed to cancel",
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
        private const val TAG = "PatientAppointmentsVM"
    }
}

package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Appointment
import com.esiri.esiriplus.core.domain.model.AppointmentStatus
import com.esiri.esiriplus.core.domain.repository.AppointmentRepository
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.ConsultationRequestRepository
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.service.ConsultationRequestRealtimeService
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.feature.patient.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val isRefreshing: Boolean = false,
    val isCancelling: String? = null,
    val errorMessage: String? = null,
    /** Active request state — shown as countdown on the appointment card */
    val activeRequestAppointmentId: String? = null,
    val activeRequestId: String? = null,
    val secondsRemaining: Int = 0,
    val isStarting: String? = null,
    /** Dialog state when doctor is unavailable */
    val showDoctorUnavailableDialog: Boolean = false,
    val unavailableAppointment: Appointment? = null,
)

/** Navigate to the chat screen when doctor accepts. */
data class AppointmentConsultationAccepted(val consultationId: String)

@HiltViewModel
class PatientAppointmentsViewModel @Inject constructor(
    private val application: Application,
    private val appointmentRepository: AppointmentRepository,
    private val consultationRequestRepository: ConsultationRequestRepository,
    private val realtimeService: ConsultationRequestRealtimeService,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientAppointmentsUiState())
    val uiState: StateFlow<PatientAppointmentsUiState> = _uiState.asStateFlow()

    /** Emitted when the doctor accepts — navigate to consultation chat. */
    private val _acceptedEvent = MutableSharedFlow<AppointmentConsultationAccepted>(extraBufferCapacity = 1)
    val acceptedEvent: SharedFlow<AppointmentConsultationAccepted> = _acceptedEvent.asSharedFlow()

    /** Emitted when doctor is unavailable — navigate to find another doctor. */
    private val _findAnotherEvent = MutableSharedFlow<Appointment>(extraBufferCapacity = 1)
    val findAnotherEvent: SharedFlow<Appointment> = _findAnotherEvent.asSharedFlow()

    private var countdownJob: Job? = null
    private var realtimeJob: Job? = null
    private var pollingJob: Job? = null

    companion object {
        private const val TAG = "PatientAppointmentsVM"
        private const val REQUEST_TTL_SECONDS = 60
    }

    init {
        loadAppointments()
    }

    fun selectTab(tab: AppointmentTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = appointmentRepository.getAppointments(limit = 100)) {
                is Result.Success -> {
                    val (upcoming, past) = splitAppointments(result.data)
                    _uiState.update {
                        it.copy(upcomingAppointments = upcoming, pastAppointments = past, isRefreshing = false)
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isRefreshing = false, errorMessage = result.message ?: application.getString(R.string.vm_failed_load_appointments))
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun loadAppointments() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = appointmentRepository.getAppointments(limit = 100)) {
                is Result.Success -> {
                    val (upcoming, past) = splitAppointments(result.data)
                    _uiState.update {
                        it.copy(upcomingAppointments = upcoming, pastAppointments = past, isLoading = false)
                    }
                }
                is Result.Error -> {
                    Log.w(TAG, "Failed to load appointments: ${result.message}")
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = result.message ?: application.getString(R.string.vm_failed_load_appointments))
                    }
                }
                is Result.Loading -> {}
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
                        it.copy(isCancelling = null, errorMessage = result.message ?: application.getString(R.string.vm_failed_cancel_appointment))
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissDoctorUnavailableDialog() {
        _uiState.update { it.copy(showDoctorUnavailableDialog = false, unavailableAppointment = null) }
    }

    fun findAnotherDoctor() {
        val appt = _uiState.value.unavailableAppointment ?: return
        _uiState.update { it.copy(showDoctorUnavailableDialog = false, unavailableAppointment = null) }
        _findAnotherEvent.tryEmit(appt)
    }

    // ── Start consultation flow ──────────────────────────────────────────────

    /**
     * Directly request the appointed doctor. Shows countdown on the card.
     * If doctor is unavailable, shows the "Find Another Doctor" dialog.
     * If doctor accepts within 60s, navigates to chat.
     */
    fun startConsultation(appointment: Appointment) {
        if (_uiState.value.isStarting != null || _uiState.value.activeRequestAppointmentId != null) return
        _uiState.update { it.copy(isStarting = appointment.appointmentId, errorMessage = null) }

        viewModelScope.launch {
            // Proactive patient token refresh
            val token = tokenManager.getAccessTokenSync()
            if (token == null || tokenManager.isTokenExpiringSoon(5)) {
                try { authRepository.refreshSession() } catch (_: Exception) {}
            }

            // Import auth token for realtime
            try {
                val freshToken = tokenManager.getAccessTokenSync()
                if (freshToken != null) {
                    supabaseClientProvider.importAuthToken(accessToken = freshToken)
                }
            } catch (_: Exception) {}

            val result = consultationRequestRepository.createRequest(
                doctorId = appointment.doctorId,
                serviceType = appointment.serviceType,
                consultationType = appointment.consultationType,
                chiefComplaint = appointment.chiefComplaint.ifBlank { application.getString(R.string.appt_scheduled_consultation) },
                appointmentId = appointment.appointmentId,
            )

            when (result) {
                is Result.Success -> {
                    val requestId = result.data.requestId
                    Log.d(TAG, "Request created: $requestId for appointment ${appointment.appointmentId}")
                    _uiState.update {
                        it.copy(
                            isStarting = null,
                            activeRequestAppointmentId = appointment.appointmentId,
                            activeRequestId = requestId,
                            secondsRemaining = REQUEST_TTL_SECONDS,
                        )
                    }
                    startCountdown(appointment)
                    startRealtimeWatch(requestId, appointment)
                }
                is Result.Error -> {
                    val msg = result.message ?: result.exception.message ?: ""
                    val isDoctorUnavailable = msg.contains("not currently available", ignoreCase = true)
                        || msg.contains("in a session", ignoreCase = true)
                        || msg.contains("doctor_in_session", ignoreCase = true)
                        || msg.contains("not available", ignoreCase = true)

                    _uiState.update { it.copy(isStarting = null) }

                    if (isDoctorUnavailable) {
                        _uiState.update {
                            it.copy(showDoctorUnavailableDialog = true, unavailableAppointment = appointment)
                        }
                    } else {
                        _uiState.update { it.copy(errorMessage = msg.ifBlank { application.getString(R.string.appt_failed_start) }) }
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    private fun startCountdown(appointment: Appointment) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = REQUEST_TTL_SECONDS
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.update { it.copy(secondsRemaining = remaining) }
            }
            // Countdown expired — doctor didn't respond
            onRequestExpired(appointment)
        }
    }

    private fun startRealtimeWatch(requestId: String, appointment: Appointment) {
        // Realtime subscription
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val sessionId = extractSessionId() ?: return@launch
            realtimeService.subscribeAsPatient(sessionId, viewModelScope)
            realtimeService.requestEvents.collect { event ->
                if (event.requestId != requestId) return@collect
                handleRequestStatus(event.status, event.consultationId, appointment)
            }
        }

        // Polling fallback every 3s — catches the status even if realtime is slow
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                if (_uiState.value.activeRequestId != requestId) break
                try {
                    val result = consultationRequestRepository.checkRequestStatus(requestId)
                    if (result is Result.Success) {
                        val status = result.data.status
                        if (status != ConsultationRequestStatus.PENDING) {
                            handleRequestStatus(
                                status.name.lowercase(),
                                result.data.consultationId,
                                appointment,
                            )
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Polling failed", e)
                }
            }
        }
    }

    private fun handleRequestStatus(status: String, consultationId: String?, appointment: Appointment) {
        when (status.lowercase()) {
            "accepted" -> {
                Log.d(TAG, "Request accepted! consultationId=$consultationId")
                countdownJob?.cancel()
                pollingJob?.cancel()
                clearActiveRequest()
                if (!consultationId.isNullOrBlank()) {
                    _acceptedEvent.tryEmit(AppointmentConsultationAccepted(consultationId))
                }
            }
            "rejected" -> {
                Log.d(TAG, "Request rejected by doctor")
                countdownJob?.cancel()
                pollingJob?.cancel()
                clearActiveRequest()
                _uiState.update {
                    it.copy(showDoctorUnavailableDialog = true, unavailableAppointment = appointment)
                }
            }
            "expired" -> {
                countdownJob?.cancel()
                pollingJob?.cancel()
                onRequestExpired(appointment)
            }
        }
    }

    private fun onRequestExpired(appointment: Appointment) {
        clearActiveRequest()
        // Doctor didn't respond — offer to find another
        _uiState.update {
            it.copy(showDoctorUnavailableDialog = true, unavailableAppointment = appointment)
        }
    }

    private fun clearActiveRequest() {
        _uiState.update {
            it.copy(activeRequestAppointmentId = null, activeRequestId = null, secondsRemaining = 0)
        }
        realtimeJob?.cancel()
        pollingJob?.cancel()
        // Tear down the realtime channel so it doesn't interfere with the consultation screen's subscriptions
        realtimeService.unsubscribeSync()
    }

    private fun extractSessionId(): String? {
        val token = tokenManager.getAccessTokenSync() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(android.util.Base64.decode(parts[1],
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
            org.json.JSONObject(payload).optString("session_id", null)
        } catch (_: Exception) { null }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        pollingJob?.cancel()
        realtimeJob?.cancel()
        realtimeService.unsubscribeSync()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun splitAppointments(all: List<Appointment>): Pair<List<Appointment>, List<Appointment>> {
        val upcoming = all.filter { apt ->
            apt.status in listOf(
                AppointmentStatus.BOOKED,
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.IN_PROGRESS,
            ) || (apt.status == AppointmentStatus.MISSED && apt.consultationId == null)
        }.sortedBy { it.scheduledAt }

        val past = all.filter { apt ->
            apt.status in listOf(
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.RESCHEDULED,
            ) || (apt.status == AppointmentStatus.MISSED && apt.consultationId != null)
        }.sortedByDescending { it.scheduledAt }

        return upcoming to past
    }
}

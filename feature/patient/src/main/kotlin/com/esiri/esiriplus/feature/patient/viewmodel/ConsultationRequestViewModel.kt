package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Base64
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.dao.PatientProfileDao
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.ConsultationRequestRepository
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.service.ConsultationRequestRealtimeService
import org.json.JSONObject
import com.esiri.esiriplus.core.network.service.RequestRealtimeEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConsultationRequestUiState(
    /** Currently active request (null = no active request, doctor buttons enabled) */
    val activeRequestId: String? = null,
    val activeRequestDoctorId: String? = null,
    val status: ConsultationRequestStatus? = null,
    /** Countdown: seconds remaining (60 → 0) */
    val secondsRemaining: Int = 0,
    /** User-facing status message */
    val statusMessage: String? = null,
    /** True while network call is in-flight */
    val isSending: Boolean = false,
    /** Error message for transient errors */
    val errorMessage: String? = null,
    /** Symptoms dialog state */
    val showSymptomsDialog: Boolean = false,
    val pendingDoctorId: String? = null,
    val pendingServiceType: String? = null,
    /** Cached patient profile fields */
    val patientAgeGroup: String? = null,
    val patientSex: String? = null,
    val patientBloodGroup: String? = null,
    val patientAllergies: String? = null,
    val patientChronicConditions: String? = null,
)

/** One-shot navigation event emitted when consultation is accepted. */
data class ConsultationAcceptedEvent(
    val consultationId: String,
)

@HiltViewModel
class ConsultationRequestViewModel @Inject constructor(
    private val consultationRequestRepository: ConsultationRequestRepository,
    private val realtimeService: ConsultationRequestRealtimeService,
    private val tokenManager: TokenManager,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val patientProfileDao: PatientProfileDao,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsultationRequestUiState())
    val uiState: StateFlow<ConsultationRequestUiState> = _uiState.asStateFlow()

    private val _acceptedEvent = MutableSharedFlow<ConsultationAcceptedEvent>(extraBufferCapacity = 1)
    val acceptedEvent: SharedFlow<ConsultationAcceptedEvent> = _acceptedEvent.asSharedFlow()

    private var countdownJob: Job? = null
    private var realtimeJob: Job? = null
    private var pollingJob: Job? = null

    init {
        observeRealtime()
        loadPatientProfile()
    }

    private fun loadPatientProfile() {
        viewModelScope.launch {
            try {
                val userId = authRepository.currentSession.first()?.user?.id ?: return@launch
                val profile = patientProfileDao.getByUserId(userId).first() ?: return@launch
                _uiState.update {
                    it.copy(
                        patientAgeGroup = profile.ageGroup,
                        patientSex = profile.sex,
                        patientBloodGroup = profile.bloodGroup,
                        patientAllergies = profile.allergies,
                        patientChronicConditions = profile.chronicConditions,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load patient profile", e)
            }
        }
    }

    private fun observeRealtime() {
        realtimeJob = viewModelScope.launch {
            // Extract the session_id from the patient JWT token
            val sessionId = getSessionIdFromToken()
            if (sessionId == null) {
                Log.w(TAG, "observeRealtime ABORT: sessionId is null (no JWT or no session_id claim)")
                return@launch
            }

            // Import auth token so Supabase Realtime authenticates as "authenticated" role.
            // Without this, Realtime connects as anon and RLS blocks all events.
            try {
                val freshToken = tokenManager.getAccessTokenSync()
                if (freshToken != null) {
                    supabaseClientProvider.importAuthToken(accessToken = freshToken)
                    Log.d(TAG, "importAuthToken succeeded for Realtime")
                }
            } catch (e: Exception) {
                Log.e(TAG, "importAuthToken FAILED — Realtime may not receive events", e)
            }

            Log.w(TAG, "Subscribing to realtime with sessionId: $sessionId")
            realtimeService.subscribeAsPatient(sessionId, viewModelScope)

            realtimeService.requestEvents.collect { event ->
                Log.w(TAG, "Received realtime event: requestId=${event.requestId} status=${event.status} consultationId=${event.consultationId}")
                handleRealtimeEvent(event)
            }
        }
    }

    private fun getSessionIdFromToken(): String? {
        val token = tokenManager.getAccessTokenSync()
        if (token == null) {
            Log.w(TAG, "getSessionIdFromToken: token is NULL")
            return null
        }
        return try {
            val parts = token.split(".")
            if (parts.size < 2) {
                Log.w(TAG, "getSessionIdFromToken: JWT has < 2 parts")
                return null
            }
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
            val sessionId = JSONObject(payload).optString("session_id", null)
            Log.w(TAG, "getSessionIdFromToken: session_id=${sessionId ?: "NULL"}")
            sessionId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode session_id from JWT", e)
            null
        }
    }

    private fun handleRealtimeEvent(event: RequestRealtimeEvent) {
        val currentRequestId = _uiState.value.activeRequestId
        Log.w(TAG, "handleRealtimeEvent: event.requestId=${event.requestId} currentRequestId=$currentRequestId")
        if (currentRequestId == null) {
            Log.w(TAG, "handleRealtimeEvent SKIP: no active request")
            return
        }
        if (event.requestId != currentRequestId) {
            Log.w(TAG, "handleRealtimeEvent SKIP: requestId mismatch")
            return
        }

        val newStatus = ConsultationRequestStatus.fromString(event.status)
        when (newStatus) {
            ConsultationRequestStatus.ACCEPTED -> {
                stopCountdown()
                Log.d(TAG, "ACCEPTED — consultationId=${event.consultationId}")
                _uiState.update {
                    it.copy(
                        status = ConsultationRequestStatus.ACCEPTED,
                        statusMessage = "Doctor accepted! Opening chat...",
                        secondsRemaining = 0,
                    )
                }
                val consultationId = event.consultationId
                if (consultationId != null) {
                    Log.d(TAG, "Emitting acceptedEvent with consultationId=$consultationId")
                    _acceptedEvent.tryEmit(ConsultationAcceptedEvent(consultationId))
                } else {
                    Log.e(TAG, "ACCEPTED but consultationId is NULL!")
                }
                // Clear request state after short delay for UI feedback
                viewModelScope.launch {
                    delay(2000)
                    Log.d(TAG, "clearRequest() after accepted delay")
                    clearRequest()
                }
            }
            ConsultationRequestStatus.REJECTED -> {
                stopCountdown()
                _uiState.update {
                    it.copy(
                        status = ConsultationRequestStatus.REJECTED,
                        statusMessage = "Doctor declined the request. You can request another doctor.",
                        secondsRemaining = 0,
                    )
                }
                viewModelScope.launch {
                    delay(3000)
                    clearRequest()
                }
            }
            ConsultationRequestStatus.EXPIRED -> {
                stopCountdown()
                _uiState.update {
                    it.copy(
                        status = ConsultationRequestStatus.EXPIRED,
                        statusMessage = "Doctor did not respond. You can try another doctor.",
                        secondsRemaining = 0,
                    )
                }
                viewModelScope.launch {
                    delay(3000)
                    clearRequest()
                }
            }
            else -> {}
        }
    }

    /**
     * Patient taps "Request Consultation" — opens symptoms entry dialog.
     */
    fun requestConsultation(doctorId: String, serviceType: String) {
        if (_uiState.value.activeRequestId != null || _uiState.value.isSending) return
        _uiState.update {
            it.copy(
                showSymptomsDialog = true,
                pendingDoctorId = doctorId,
                pendingServiceType = serviceType,
            )
        }
    }

    /**
     * Patient confirms symptoms and sends the request.
     */
    fun confirmAndSendRequest(symptoms: String) {
        val state = _uiState.value
        val doctorId = state.pendingDoctorId ?: return
        val serviceType = state.pendingServiceType ?: return

        _uiState.update {
            it.copy(showSymptomsDialog = false, pendingDoctorId = null, pendingServiceType = null)
        }

        sendRequest(
            doctorId = doctorId,
            serviceType = serviceType,
            symptoms = symptoms.trim().ifBlank { null },
        )
    }

    fun dismissSymptomsDialog() {
        _uiState.update {
            it.copy(showSymptomsDialog = false, pendingDoctorId = null, pendingServiceType = null)
        }
    }

    /**
     * Sends the consultation request to the server.
     */
    fun sendRequest(
        doctorId: String,
        serviceType: String,
        consultationType: String = "chat",
        chiefComplaint: String = "General consultation",
        symptoms: String? = null,
    ) {
        // Prevent duplicate requests
        if (_uiState.value.activeRequestId != null || _uiState.value.isSending) return

        val state = _uiState.value
        _uiState.update {
            it.copy(
                isSending = true,
                errorMessage = null,
                activeRequestDoctorId = doctorId,
            )
        }

        viewModelScope.launch {
            when (val result = consultationRequestRepository.createRequest(
                doctorId = doctorId,
                serviceType = serviceType,
                consultationType = consultationType,
                chiefComplaint = chiefComplaint,
                symptoms = symptoms,
                patientAgeGroup = state.patientAgeGroup,
                patientSex = state.patientSex,
                patientBloodGroup = state.patientBloodGroup,
                patientAllergies = state.patientAllergies,
                patientChronicConditions = state.patientChronicConditions,
            )) {
                is Result.Success -> {
                    val request = result.data
                    _uiState.update {
                        it.copy(
                            activeRequestId = request.requestId,
                            activeRequestDoctorId = doctorId,
                            status = ConsultationRequestStatus.PENDING,
                            statusMessage = "Waiting for doctor response...",
                            isSending = false,
                            secondsRemaining = REQUEST_TTL_SECONDS,
                        )
                    }
                    startCountdown(request.requestId)
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to create request", result.exception)
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            activeRequestDoctorId = null,
                            errorMessage = result.message ?: result.exception.message
                                ?: "Failed to send request",
                        )
                    }
                }
                is Result.Loading -> {} // Not emitted by repository
            }
        }
    }

    private fun startCountdown(requestId: String) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = REQUEST_TTL_SECONDS
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.update { it.copy(secondsRemaining = remaining) }
            }
            // Timer expired — notify server
            onCountdownExpired(requestId)
        }

        // Belt-and-suspenders: poll server every 3s as fallback for Realtime
        startPolling(requestId)
    }

    /**
     * Polls the server for request status changes every [POLL_INTERVAL_MS].
     * This is a resilience mechanism in case Realtime events are not delivered
     * (e.g., RLS issues, WebSocket disconnection, network flake).
     */
    private fun startPolling(requestId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                // Only poll if request is still pending
                if (_uiState.value.status != ConsultationRequestStatus.PENDING) break
                try {
                    val result = consultationRequestRepository.checkRequestStatus(requestId)
                    if (result is Result.Success) {
                        val serverStatus = result.data.status
                        if (serverStatus != ConsultationRequestStatus.PENDING) {
                            Log.d(TAG, "Polling detected status change: $serverStatus (consultationId=${result.data.consultationId})")
                            handleRealtimeEvent(
                                RequestRealtimeEvent(
                                    requestId = requestId,
                                    status = serverStatus.name.lowercase(),
                                    consultationId = result.data.consultationId,
                                ),
                            )
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Polling failed (will retry)", e)
                }
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun onCountdownExpired(requestId: String) {
        // Only expire if still pending (realtime may have resolved it already)
        if (_uiState.value.status != ConsultationRequestStatus.PENDING) return

        _uiState.update {
            it.copy(
                status = ConsultationRequestStatus.EXPIRED,
                statusMessage = "Doctor did not respond. You can try another doctor.",
                secondsRemaining = 0,
            )
        }

        // Tell server to mark as expired (server validates timestamp)
        try {
            consultationRequestRepository.expireRequest(requestId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to expire request on server", e)
        }

        delay(3000)
        clearRequest()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissStatus() {
        clearRequest()
    }

    private fun clearRequest() {
        _uiState.update {
            it.copy(
                activeRequestId = null,
                activeRequestDoctorId = null,
                status = null,
                secondsRemaining = 0,
                statusMessage = null,
                isSending = false,
                errorMessage = null,
                showSymptomsDialog = false,
                pendingDoctorId = null,
                pendingServiceType = null,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCountdown()
        viewModelScope.launch {
            realtimeService.unsubscribe()
        }
    }

    companion object {
        private const val TAG = "ConsultReqVM"
        const val REQUEST_TTL_SECONDS = 60
        private const val POLL_INTERVAL_MS = 3_000L
    }
}

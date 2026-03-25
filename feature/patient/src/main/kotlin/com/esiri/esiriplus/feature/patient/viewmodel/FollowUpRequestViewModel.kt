package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Base64
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.core.domain.repository.ConsultationRequestRepository
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.service.ConsultationRequestRealtimeService
import com.esiri.esiriplus.core.network.service.RequestRealtimeEvent
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class FollowUpRequestUiState(
    val parentConsultationId: String = "",
    val doctorId: String = "",
    val serviceType: String = "",
    /** Active request id returned by the server */
    val activeRequestId: String? = null,
    val status: FollowUpStatus = FollowUpStatus.SENDING,
    /** Countdown seconds remaining (60 -> 0) */
    val secondsRemaining: Int = REQUEST_TTL_SECONDS,
    /** User-facing status message */
    val statusMessage: String? = null,
    /** Error message for transient errors */
    val errorMessage: String? = null,
) {
    companion object {
        const val REQUEST_TTL_SECONDS = 60
    }
}

enum class FollowUpStatus {
    SENDING,
    WAITING,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    ERROR,
}

/** One-shot event emitted when the follow-up consultation is accepted. */
data class FollowUpAcceptedEvent(val consultationId: String)

@HiltViewModel
class FollowUpRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val consultationRequestRepository: ConsultationRequestRepository,
    private val realtimeService: ConsultationRequestRealtimeService,
    private val tokenManager: TokenManager,
    private val supabaseClientProvider: SupabaseClientProvider,
) : ViewModel() {

    private val parentConsultationId: String = checkNotNull(savedStateHandle["parentConsultationId"])
    private val doctorId: String = checkNotNull(savedStateHandle["doctorId"])
    private val serviceType: String = checkNotNull(savedStateHandle["serviceType"])

    private val _uiState = MutableStateFlow(
        FollowUpRequestUiState(
            parentConsultationId = parentConsultationId,
            doctorId = doctorId,
            serviceType = serviceType,
        ),
    )
    val uiState: StateFlow<FollowUpRequestUiState> = _uiState.asStateFlow()

    private val _acceptedEvent = MutableSharedFlow<FollowUpAcceptedEvent>(extraBufferCapacity = 1)
    val acceptedEvent: SharedFlow<FollowUpAcceptedEvent> = _acceptedEvent.asSharedFlow()

    private var countdownJob: Job? = null
    private var realtimeJob: Job? = null
    private var pollingJob: Job? = null

    init {
        observeRealtime()
        sendFollowUpRequest()
    }

    // ── Realtime ─────────────────────────────────────────────────────────────────

    private fun observeRealtime() {
        realtimeJob = viewModelScope.launch {
            val sessionId = getSessionIdFromToken()
            if (sessionId == null) {
                Log.w(TAG, "observeRealtime ABORT: sessionId is null")
                return@launch
            }

            try {
                val freshToken = tokenManager.getAccessTokenSync()
                if (freshToken != null) {
                    supabaseClientProvider.importAuthToken(accessToken = freshToken)
                }
            } catch (e: Exception) {
                Log.e(TAG, "importAuthToken FAILED", e)
            }

            realtimeService.subscribeAsPatient(sessionId, viewModelScope)

            realtimeService.requestEvents.collect { event ->
                Log.d(TAG, "Realtime event: requestId=${event.requestId} status=${event.status}")
                handleRealtimeEvent(event)
            }
        }
    }

    private fun getSessionIdFromToken(): String? {
        val token = tokenManager.getAccessTokenSync() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
            JSONObject(payload).optString("session_id", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode session_id from JWT", e)
            null
        }
    }

    private fun handleRealtimeEvent(event: RequestRealtimeEvent) {
        val currentRequestId = _uiState.value.activeRequestId ?: return
        if (event.requestId != currentRequestId) return

        val newStatus = ConsultationRequestStatus.fromString(event.status)
        when (newStatus) {
            ConsultationRequestStatus.ACCEPTED -> {
                stopCountdown()
                _uiState.update {
                    it.copy(
                        status = FollowUpStatus.ACCEPTED,
                        statusMessage = null,
                        secondsRemaining = 0,
                    )
                }
                val consultationId = event.consultationId
                if (consultationId != null) {
                    _acceptedEvent.tryEmit(FollowUpAcceptedEvent(consultationId))
                } else {
                    Log.e(TAG, "ACCEPTED but consultationId is NULL")
                }
            }
            ConsultationRequestStatus.REJECTED -> {
                stopCountdown()
                _uiState.update {
                    it.copy(
                        status = FollowUpStatus.REJECTED,
                        statusMessage = null,
                        secondsRemaining = 0,
                    )
                }
            }
            ConsultationRequestStatus.EXPIRED -> {
                stopCountdown()
                _uiState.update {
                    it.copy(
                        status = FollowUpStatus.EXPIRED,
                        statusMessage = null,
                        secondsRemaining = 0,
                    )
                }
            }
            else -> {}
        }
    }

    // ── Send request ─────────────────────────────────────────────────────────────

    private fun sendFollowUpRequest() {
        viewModelScope.launch {
            when (val result = consultationRequestRepository.createRequest(
                doctorId = doctorId,
                serviceType = serviceType,
                serviceTier = "ROYAL",
                consultationType = "chat",
                chiefComplaint = "Follow-up consultation",
                isFollowUp = true,
                parentConsultationId = parentConsultationId,
            )) {
                is Result.Success -> {
                    val request = result.data
                    _uiState.update {
                        it.copy(
                            activeRequestId = request.requestId,
                            status = FollowUpStatus.WAITING,
                            secondsRemaining = FollowUpRequestUiState.REQUEST_TTL_SECONDS,
                        )
                    }
                    startCountdown(request.requestId)
                }
                is Result.Error -> {
                    val msg = result.message ?: result.exception.message ?: ""
                    Log.e(TAG, "Failed to create follow-up request: $msg", result.exception)
                    // Doctor unavailable/in-session → show choice dialog (same as EXPIRED)
                    val isDoctorUnavailable = msg.contains("not currently available", ignoreCase = true)
                        || msg.contains("in a session", ignoreCase = true)
                        || msg.contains("doctor_in_session", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            status = if (isDoctorUnavailable) FollowUpStatus.EXPIRED else FollowUpStatus.ERROR,
                            errorMessage = if (isDoctorUnavailable) null else msg.ifBlank { "Failed to send follow-up request" },
                        )
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    // ── Countdown & polling ──────────────────────────────────────────────────────

    private fun startCountdown(requestId: String) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = FollowUpRequestUiState.REQUEST_TTL_SECONDS
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.update { it.copy(secondsRemaining = remaining) }
            }
            onCountdownExpired(requestId)
        }
        startPolling(requestId)
    }

    private fun startPolling(requestId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (_uiState.value.status != FollowUpStatus.WAITING) break
                try {
                    val result = consultationRequestRepository.checkRequestStatus(requestId)
                    if (result is Result.Success) {
                        val serverStatus = result.data.status
                        if (serverStatus != ConsultationRequestStatus.PENDING) {
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
        if (_uiState.value.status != FollowUpStatus.WAITING) return

        _uiState.update {
            it.copy(
                status = FollowUpStatus.EXPIRED,
                statusMessage = null,
                secondsRemaining = 0,
            )
        }

        try {
            consultationRequestRepository.expireRequest(requestId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to expire request on server", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCountdown()
        realtimeService.unsubscribeSync()
    }

    companion object {
        private const val TAG = "FollowUpReqVM"
        private const val POLL_INTERVAL_MS = 3_000L
    }
}

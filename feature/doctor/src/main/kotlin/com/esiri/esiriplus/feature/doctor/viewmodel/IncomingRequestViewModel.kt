package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.ConsultationRequestRepository
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.interceptor.TokenRefresher
import com.esiri.esiriplus.core.network.service.ConsultationRequestRealtimeService
import com.esiri.esiriplus.core.network.service.RequestRealtimeEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class IncomingRequestUiState(
    /** Non-null when there is an active incoming request to show */
    val requestId: String? = null,
    val patientSessionId: String? = null,
    val serviceType: String? = null,
    val secondsRemaining: Int = 0,
    val isResponding: Boolean = false,
    val responseStatus: ConsultationRequestStatus? = null,
    val errorMessage: String? = null,
    /** True when the accept call failed but can be retried (countdown paused). */
    val canRetry: Boolean = false,
)

data class ConsultationStartedEvent(
    val consultationId: String,
)

@HiltViewModel
class IncomingRequestViewModel @Inject constructor(
    private val consultationRequestRepository: ConsultationRequestRepository,
    private val realtimeService: ConsultationRequestRealtimeService,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val tokenRefresher: TokenRefresher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomingRequestUiState())
    val uiState: StateFlow<IncomingRequestUiState> = _uiState.asStateFlow()

    private val _consultationStarted = MutableSharedFlow<ConsultationStartedEvent>(extraBufferCapacity = 1)
    val consultationStarted: SharedFlow<ConsultationStartedEvent> = _consultationStarted.asSharedFlow()

    private var countdownJob: Job? = null

    init {
        observeRealtime()
    }

    private fun observeRealtime() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first() ?: return@launch
            val doctorId = session.user.id
            Log.d(TAG, "Subscribing to realtime requests for doctor=$doctorId")
            realtimeService.subscribeAsDoctor(doctorId, viewModelScope)

            realtimeService.requestEvents.collect { event ->
                handleRealtimeEvent(event)
            }
        }
    }

    private fun handleRealtimeEvent(event: RequestRealtimeEvent) {
        val status = ConsultationRequestStatus.fromString(event.status)
        Log.d(TAG, "Realtime event: requestId=${event.requestId}, status=$status")

        when (status) {
            ConsultationRequestStatus.PENDING -> {
                // New incoming request — show dialog with countdown
                val currentRequest = _uiState.value.requestId
                if (currentRequest != null) {
                    // Already handling a request, ignore new ones
                    Log.d(TAG, "Ignoring new request, already handling $currentRequest")
                    return
                }
                showIncomingRequest(event)
            }
            ConsultationRequestStatus.EXPIRED -> {
                // Request expired (patient or server timed it out)
                if (event.requestId == _uiState.value.requestId) {
                    stopCountdown()
                    _uiState.update {
                        it.copy(
                            responseStatus = ConsultationRequestStatus.EXPIRED,
                            secondsRemaining = 0,
                            canRetry = false,
                        )
                    }
                    viewModelScope.launch {
                        delay(2000)
                        dismissRequest()
                    }
                }
            }
            ConsultationRequestStatus.ACCEPTED -> {
                // Our own accept was confirmed via realtime — if we missed the HTTP
                // response (e.g., network timeout after server processed it), use
                // the consultation_id from the realtime event as a fallback.
                if (event.requestId == _uiState.value.requestId &&
                    _uiState.value.responseStatus != ConsultationRequestStatus.ACCEPTED
                ) {
                    Log.w(TAG, "Accept confirmed via REALTIME fallback: consultationId=${event.consultationId}")
                    stopCountdown()
                    _uiState.update {
                        it.copy(
                            isResponding = false,
                            responseStatus = ConsultationRequestStatus.ACCEPTED,
                            errorMessage = null,
                            canRetry = false,
                        )
                    }
                    val consultationId = event.consultationId
                    if (!consultationId.isNullOrBlank()) {
                        _consultationStarted.tryEmit(ConsultationStartedEvent(consultationId))
                    }
                    viewModelScope.launch {
                        delay(1500)
                        dismissRequest()
                    }
                }
            }
            else -> {
                // REJECTED — our own action, handled in reject method
            }
        }
    }

    private fun showIncomingRequest(event: RequestRealtimeEvent) {
        _uiState.update {
            IncomingRequestUiState(
                requestId = event.requestId,
                patientSessionId = event.patientSessionId,
                serviceType = event.serviceType,
                secondsRemaining = REQUEST_TTL_SECONDS,
            )
        }
        startCountdown(event.requestId)
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
            // Timer expired locally — auto-dismiss only if we haven't responded
            if (_uiState.value.requestId == requestId &&
                _uiState.value.responseStatus == null &&
                !_uiState.value.canRetry
            ) {
                _uiState.update {
                    it.copy(responseStatus = ConsultationRequestStatus.EXPIRED)
                }
                delay(2000)
                dismissRequest()
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    fun acceptRequest() {
        val requestId = _uiState.value.requestId ?: return
        if (_uiState.value.isResponding) return

        Log.d(TAG, "acceptRequest: requestId=$requestId")
        _uiState.update { it.copy(isResponding = true, errorMessage = null, canRetry = false) }

        viewModelScope.launch {
            if (!ensureFreshToken()) {
                Log.e(TAG, "acceptRequest: token refresh failed")
                // Stop countdown so the error stays visible and doesn't auto-dismiss
                stopCountdown()
                _uiState.update {
                    it.copy(
                        isResponding = false,
                        errorMessage = "Session expired. Please sign in again.",
                        canRetry = true,
                    )
                }
                return@launch
            }
            when (val result = consultationRequestRepository.acceptRequest(requestId)) {
                is Result.Success -> {
                    Log.d(TAG, "acceptRequest SUCCESS: consultationId=${result.data.consultationId}")
                    stopCountdown()
                    _uiState.update {
                        it.copy(
                            isResponding = false,
                            responseStatus = ConsultationRequestStatus.ACCEPTED,
                            errorMessage = null,
                            canRetry = false,
                        )
                    }
                    val consultationId = result.data.consultationId
                    if (consultationId != null) {
                        _consultationStarted.tryEmit(ConsultationStartedEvent(consultationId))
                    } else {
                        Log.e(TAG, "acceptRequest: SUCCESS but consultationId is null!")
                    }
                    delay(1500)
                    dismissRequest()
                }
                is Result.Error -> {
                    Log.e(TAG, "acceptRequest FAILED: message=${result.message}", result.exception)
                    // CRITICAL: Stop countdown so the error stays visible.
                    // Without this, the timer auto-dismisses the dialog and the
                    // doctor sees a flash of error before being dumped back to the
                    // dashboard with no way to retry.
                    stopCountdown()
                    _uiState.update {
                        it.copy(
                            isResponding = false,
                            errorMessage = result.message ?: "Failed to accept. Try again.",
                            canRetry = true,
                        )
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun rejectRequest() {
        val requestId = _uiState.value.requestId ?: return
        if (_uiState.value.isResponding) return

        _uiState.update { it.copy(isResponding = true) }

        viewModelScope.launch {
            if (!ensureFreshToken()) {
                stopCountdown()
                _uiState.update {
                    it.copy(
                        isResponding = false,
                        errorMessage = "Session expired. Please sign in again.",
                        canRetry = true,
                    )
                }
                return@launch
            }
            when (val result = consultationRequestRepository.rejectRequest(requestId)) {
                is Result.Success -> {
                    stopCountdown()
                    _uiState.update {
                        it.copy(
                            isResponding = false,
                            responseStatus = ConsultationRequestStatus.REJECTED,
                        )
                    }
                    delay(1500)
                    dismissRequest()
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to reject request", result.exception)
                    stopCountdown()
                    _uiState.update {
                        it.copy(
                            isResponding = false,
                            errorMessage = result.message ?: "Failed to reject.",
                            canRetry = true,
                        )
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun dismissRequest() {
        stopCountdown()
        _uiState.update { IncomingRequestUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        stopCountdown()
        viewModelScope.launch {
            realtimeService.unsubscribe()
        }
    }

    /**
     * Ensure the OkHttp TokenManager has a fresh JWT before making edge function calls.
     * The doctor may have been idle on the dashboard (only realtime active, no OkHttp calls),
     * so the token in EncryptedTokenStorage could be expired.
     *
     * @return true if the token is valid, false if refresh failed and the call should be aborted
     */
    private suspend fun ensureFreshToken(): Boolean {
        if (!tokenManager.isTokenExpiringSoon(thresholdMinutes = 1)) {
            return true // Token is still valid
        }

        Log.d(TAG, "Token expiring soon, attempting refresh")
        val refreshToken = tokenManager.getRefreshTokenSync()
        if (refreshToken == null) {
            Log.w(TAG, "No refresh token available")
            return false
        }

        val refreshed = withContext(Dispatchers.IO) {
            tokenRefresher.refreshToken(refreshToken)
        }
        Log.d(TAG, "Token refresh result: $refreshed")
        return refreshed
    }

    companion object {
        private const val TAG = "IncomingReqVM"
        private const val REQUEST_TTL_SECONDS = 60
    }
}

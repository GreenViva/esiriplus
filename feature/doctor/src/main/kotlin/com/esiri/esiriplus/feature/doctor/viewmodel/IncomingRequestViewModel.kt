package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.ConsultationRequestRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
)

data class ConsultationStartedEvent(
    val consultationId: String,
)

@HiltViewModel
class IncomingRequestViewModel @Inject constructor(
    private val consultationRequestRepository: ConsultationRequestRepository,
    private val realtimeService: ConsultationRequestRealtimeService,
    private val authRepository: AuthRepository,
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
            realtimeService.subscribeAsDoctor(doctorId, viewModelScope)

            realtimeService.requestEvents.collect { event ->
                handleRealtimeEvent(event)
            }
        }
    }

    private fun handleRealtimeEvent(event: RequestRealtimeEvent) {
        val status = ConsultationRequestStatus.fromString(event.status)

        when (status) {
            ConsultationRequestStatus.PENDING -> {
                // New incoming request — show dialog with countdown
                val currentRequest = _uiState.value.requestId
                if (currentRequest != null) {
                    // Already handling a request, ignore new ones
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
                        )
                    }
                    viewModelScope.launch {
                        delay(2000)
                        dismissRequest()
                    }
                }
            }
            else -> {
                // ACCEPTED or REJECTED — these are our own actions, handled in accept/reject methods
            }
        }
    }

    private fun showIncomingRequest(event: RequestRealtimeEvent) {
        _uiState.update {
            IncomingRequestUiState(
                requestId = event.requestId,
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
            // Timer expired locally — auto-dismiss
            if (_uiState.value.requestId == requestId &&
                _uiState.value.responseStatus == null
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

        _uiState.update { it.copy(isResponding = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = consultationRequestRepository.acceptRequest(requestId)) {
                is Result.Success -> {
                    stopCountdown()
                    _uiState.update {
                        it.copy(
                            isResponding = false,
                            responseStatus = ConsultationRequestStatus.ACCEPTED,
                        )
                    }
                    val consultationId = result.data.consultationId
                    if (consultationId != null) {
                        _consultationStarted.tryEmit(ConsultationStartedEvent(consultationId))
                    }
                    delay(1500)
                    dismissRequest()
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to accept request", result.exception)
                    _uiState.update {
                        it.copy(
                            isResponding = false,
                            errorMessage = result.message ?: "Failed to accept. Try again.",
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
                    _uiState.update {
                        it.copy(
                            isResponding = false,
                            errorMessage = result.message ?: "Failed to reject.",
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

    companion object {
        private const val TAG = "IncomingReqVM"
        private const val REQUEST_TTL_SECONDS = 60
    }
}

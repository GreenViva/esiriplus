package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.RoyalCheckinEscalationService
import com.esiri.esiriplus.core.network.service.RoyalEscalationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoyalCheckinCoverageUiState(
    val isLoading: Boolean = true,
    val escalations: List<RoyalEscalationItem> = emptyList(),
    val error: String? = null,
    val autoAcceptError: String? = null,
    /** Escalation currently being acted on (start_call). */
    val callingEscalationId: String? = null,
    /** Per-escalation → call_id once start_call returns. */
    val callIds: Map<String, String> = emptyMap(),
    /** Per-escalation → measured wall-clock seconds once the CO returns. */
    val durations: Map<String, Int> = emptyMap(),
    /** Per-escalation → whether the call qualified for payment (>60s). */
    val qualifying: Map<String, Boolean> = emptyMap(),
    /** Set when complete_escalation succeeds. */
    val completedEscalationIds: Set<String> = emptySet(),
)

sealed class RoyalCheckinCoverageEvent {
    /**
     * Tell the host to start a VideoSDK voice call to the patient. The host
     * navigates to DoctorVideoCallRoute with the supplied [consultationId] and
     * [roomId] so the existing call screen handles join / hangup. When that
     * screen unwinds, the VM's [markCallEnded] is invoked with the wall-clock
     * duration so end_call can be fired.
     */
    data class StartCall(
        val callId: String,
        val roomId: String,
        val consultationId: String,
        val patientSessionId: String,
    ) : RoyalCheckinCoverageEvent()
}

@HiltViewModel
class RoyalCheckinCoverageViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val service: RoyalCheckinEscalationService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoyalCheckinCoverageUiState())
    val uiState: StateFlow<RoyalCheckinCoverageUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RoyalCheckinCoverageEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RoyalCheckinCoverageEvent> = _events.asSharedFlow()

    /**
     * Set when the CO opened the screen from a ring push. The VM accepts the
     * ring once on first load, then drops the flag to avoid retrying on
     * reload.
     */
    private val autoAcceptEscalationId: String? =
        savedStateHandle["autoAcceptEscalationId"]
    private var autoAcceptAttempted = false

    /** Active call clock — set by [onCall] start, consumed by [markActiveCallEnded]. */
    private data class ActiveCall(
        val escalationId: String,
        val callId: String,
        val startedAtMs: Long,
    )
    private var activeCall: ActiveCall? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            if (!autoAcceptAttempted && !autoAcceptEscalationId.isNullOrBlank()) {
                autoAcceptAttempted = true
                when (val r = service.acceptRing(autoAcceptEscalationId)) {
                    is ApiResult.Success -> Log.d(TAG, "Auto-accepted ring $autoAcceptEscalationId")
                    is ApiResult.Error -> _uiState.update {
                        it.copy(autoAcceptError = r.message ?: "Couldn't accept that escalation.")
                    }
                    is ApiResult.NetworkError -> _uiState.update {
                        it.copy(autoAcceptError = "Network error.")
                    }
                    is ApiResult.Unauthorized -> _uiState.update {
                        it.copy(autoAcceptError = "Session expired.")
                    }
                }
            }

            when (val r = service.listActiveEscalations()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, escalations = r.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = r.message ?: "Failed to load escalations")
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "Network error.")
                }
                is ApiResult.Unauthorized -> _uiState.update {
                    it.copy(isLoading = false, error = "Session expired.")
                }
            }
        }
    }

    fun onAcceptRing(escalationId: String) {
        viewModelScope.launch {
            when (val r = service.acceptRing(escalationId)) {
                is ApiResult.Success -> load()
                is ApiResult.Error -> _uiState.update { it.copy(error = r.message ?: "Accept failed") }
                is ApiResult.NetworkError -> _uiState.update { it.copy(error = "Network error.") }
                is ApiResult.Unauthorized -> _uiState.update { it.copy(error = "Session expired.") }
            }
        }
    }

    fun onDeclineRing(escalationId: String) {
        viewModelScope.launch {
            service.declineRing(escalationId)
            load()
        }
    }

    fun onCall(escalationId: String, patientSessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(callingEscalationId = escalationId, error = null) }
            when (val r = service.startCall(escalationId)) {
                is ApiResult.Success -> {
                    val data = r.data
                    val callId = data.callId
                    val roomId = data.roomId
                    val consultationId = data.consultationId
                    if (data.ok && !callId.isNullOrBlank() && !roomId.isNullOrBlank()
                        && !consultationId.isNullOrBlank()) {
                        activeCall = ActiveCall(
                            escalationId = escalationId,
                            callId = callId,
                            startedAtMs = System.currentTimeMillis(),
                        )
                        _uiState.update {
                            it.copy(
                                callingEscalationId = null,
                                callIds = it.callIds + (escalationId to callId),
                            )
                        }
                        _events.tryEmit(
                            RoyalCheckinCoverageEvent.StartCall(
                                callId = callId,
                                roomId = roomId,
                                consultationId = consultationId,
                                patientSessionId = patientSessionId,
                            ),
                        )
                    } else {
                        _uiState.update {
                            it.copy(callingEscalationId = null, error = "Could not start call.")
                        }
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(callingEscalationId = null, error = r.message ?: "Failed to start call")
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(callingEscalationId = null, error = "Network error.")
                }
                is ApiResult.Unauthorized -> _uiState.update {
                    it.copy(callingEscalationId = null, error = "Session expired.")
                }
            }
        }
    }

    /**
     * Called by the screen when it resumes after the video-call screen
     * unwinds. Computes wall-clock duration and fires end_call. We default
     * patient_accepted=true (the CO returned from the call) and let the
     * server's >60s gate determine payment eligibility — the simplest
     * heuristic that lines up with the user's spec for MVP.
     */
    fun markActiveCallEnded() {
        val active = activeCall ?: return
        activeCall = null
        val durationMs = System.currentTimeMillis() - active.startedAtMs
        val durationSec = (durationMs / 1000).toInt().coerceAtLeast(0)
        val accepted = true
        viewModelScope.launch {
            service.endCall(active.callId, accepted, durationSec)
            _uiState.update {
                it.copy(
                    durations = it.durations + (active.escalationId to durationSec),
                    qualifying = it.qualifying + (active.escalationId to (durationSec > 60)),
                )
            }
        }
    }

    fun onCompleteEscalation(escalationId: String) {
        viewModelScope.launch {
            when (val r = service.completeEscalation(escalationId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(completedEscalationIds = it.completedEscalationIds + escalationId)
                    }
                    load()
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(error = r.message ?: "Failed to complete escalation")
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(error = "Network error.")
                }
                is ApiResult.Unauthorized -> _uiState.update {
                    it.copy(error = "Session expired.")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearAutoAcceptError() {
        _uiState.update { it.copy(autoAcceptError = null) }
    }

    companion object {
        private const val TAG = "RoyalCheckinCoverageVM"
    }
}

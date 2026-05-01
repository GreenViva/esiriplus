package com.esiri.esiriplus.feature.doctor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.AcceptedReminder
import com.esiri.esiriplus.core.network.service.MedicationReminderService
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

data class MedicalReminderListUiState(
    val isLoading: Boolean = true,
    val reminders: List<AcceptedReminder> = emptyList(),
    val error: String? = null,
    val callingEventId: String? = null,
    /** Set when the screen was opened from a ring push and auto-accept failed (e.g. expired). */
    val autoAcceptError: String? = null,
)

sealed class MedicalReminderEvent {
    /**
     * Tell the host to start a VideoSDK call to [patientSessionId] using the
     * pre-created [roomId]. The same plumbing as a doctor-initiated Royal
     * call — see RoyalClientsScreen for the existing entry point.
     */
    data class StartCall(
        val eventId: String,
        val roomId: String,
        val patientSessionId: String,
    ) : MedicalReminderEvent()
}

@HiltViewModel
class MedicalReminderListViewModel @Inject constructor(
    private val application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val service: MedicationReminderService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalReminderListUiState())
    val uiState: StateFlow<MedicalReminderListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MedicalReminderEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MedicalReminderEvent> = _events.asSharedFlow()

    /**
     * Optional event id passed when the screen is opened from a ring push
     * (the nurse's tap implicitly accepts the ring). Auto-accept fires once
     * on first load.
     */
    private val autoAcceptEventId: String? =
        savedStateHandle["autoAcceptEventId"]
    private var autoAcceptAttempted = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. If we arrived via a ring push, accept it first so the event
            //    transitions to nurse_accepted and shows up in the list.
            if (!autoAcceptAttempted && !autoAcceptEventId.isNullOrBlank()) {
                autoAcceptAttempted = true
                when (val r = service.acceptRing(autoAcceptEventId)) {
                    is ApiResult.Success -> Log.d(TAG, "Auto-accepted ring $autoAcceptEventId")
                    is ApiResult.Error -> _uiState.update {
                        it.copy(autoAcceptError = r.message ?: "Couldn't accept that reminder.")
                    }
                    is ApiResult.NetworkError -> _uiState.update {
                        it.copy(autoAcceptError = "Network error.")
                    }
                    is ApiResult.Unauthorized -> _uiState.update {
                        it.copy(autoAcceptError = "Session expired.")
                    }
                }
            }

            // 2. Fetch the list of accepted-but-not-yet-called reminders.
            when (val r = service.listAcceptedReminders()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isLoading = false, reminders = r.data)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = r.message ?: "Failed to load reminders")
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

    fun onCall(reminder: AcceptedReminder) {
        viewModelScope.launch {
            _uiState.update { it.copy(callingEventId = reminder.eventId) }

            // If still in ringing state, accept it first. Server is idempotent
            // for already-accepted rows so the extra call is safe.
            if (reminder.status == "nurse_ringing" || reminder.status == "nurse_notified") {
                when (val ar = service.acceptRing(reminder.eventId)) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                callingEventId = null,
                                error = ar.message ?: "Ring expired or unavailable.",
                            )
                        }
                        return@launch
                    }
                    is ApiResult.NetworkError -> {
                        _uiState.update {
                            it.copy(callingEventId = null, error = "Network error.")
                        }
                        return@launch
                    }
                    is ApiResult.Unauthorized -> {
                        _uiState.update {
                            it.copy(callingEventId = null, error = "Session expired.")
                        }
                        return@launch
                    }
                }
            }

            when (val r = service.startCall(reminder.eventId)) {
                is ApiResult.Success -> {
                    val roomId = r.data.roomId
                    val patientSessionId = r.data.patientSessionId
                    if (r.data.ok && !roomId.isNullOrBlank() && !patientSessionId.isNullOrBlank()) {
                        _events.tryEmit(
                            MedicalReminderEvent.StartCall(
                                eventId = reminder.eventId,
                                roomId = roomId,
                                patientSessionId = patientSessionId,
                            ),
                        )
                    } else {
                        _uiState.update {
                            it.copy(callingEventId = null, error = "Could not start call.")
                        }
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(callingEventId = null, error = r.message ?: "Failed to start call")
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(callingEventId = null, error = "Network error.")
                }
                is ApiResult.Unauthorized -> _uiState.update {
                    it.copy(callingEventId = null, error = "Session expired.")
                }
            }
        }
    }

    /**
     * Mark the call completed AFTER the call ends. Called by the host once
     * the VideoSDK call has finished and lasted long enough to count.
     * Server-side ≥60s gating + 2,000 TZS earning happens in the callback fn.
     */
    fun onCallCompleted(eventId: String) {
        viewModelScope.launch {
            service.markCompleted(eventId)
            // Refresh: the entry should self-delete from the list.
            _uiState.update { it.copy(callingEventId = null) }
            load()
        }
    }

    fun clearAutoAcceptError() {
        _uiState.update { it.copy(autoAcceptError = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val TAG = "MedReminderListVM"
    }
}

package com.esiri.esiriplus.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class CallServiceState(
    val consultationId: String,
    val callType: String,
    val isVideo: Boolean,
    val durationSeconds: Int = 0,
    val isActive: Boolean = true,
)

@Singleton
class CallForegroundServiceStateHolder @Inject constructor() {

    private val _state = MutableStateFlow<CallServiceState?>(null)
    val state: StateFlow<CallServiceState?> = _state.asStateFlow()

    fun start(consultationId: String, callType: String, isVideo: Boolean) {
        _state.value = CallServiceState(
            consultationId = consultationId,
            callType = callType,
            isVideo = isVideo,
        )
    }

    fun updateDuration(seconds: Int) {
        _state.value = _state.value?.copy(durationSeconds = seconds)
    }

    fun stop() {
        _state.value = _state.value?.copy(isActive = false)
        _state.value = null
    }
}

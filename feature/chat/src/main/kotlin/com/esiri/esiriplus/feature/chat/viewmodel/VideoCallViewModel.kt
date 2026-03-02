package com.esiri.esiriplus.feature.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.CallQuality
import com.esiri.esiriplus.core.domain.model.CallType
import com.esiri.esiriplus.core.domain.model.VideoCall
import com.esiri.esiriplus.core.domain.repository.VideoCallRepository
import com.esiri.esiriplus.core.domain.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import live.videosdk.rtc.android.CustomStreamTrack
import live.videosdk.rtc.android.Meeting
import live.videosdk.rtc.android.Participant
import live.videosdk.rtc.android.Stream
import live.videosdk.rtc.android.VideoSDK
import live.videosdk.rtc.android.listeners.MeetingEventListener
import live.videosdk.rtc.android.listeners.ParticipantEventListener
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

enum class CallPhase {
    REQUESTING_PERMISSIONS,
    CONNECTING,
    WAITING_FOR_PARTICIPANT,
    IN_CALL,
    ENDED,
    ERROR,
}

data class VideoCallUiState(
    val callPhase: CallPhase = CallPhase.REQUESTING_PERMISSIONS,
    val isMicEnabled: Boolean = true,
    val isCameraEnabled: Boolean = true,
    val callDurationSeconds: Int = 0,
    val remoteParticipantJoined: Boolean = false,
    val remoteParticipantName: String? = null,
    val error: String? = null,
    val callType: CallType = CallType.VIDEO,
)

@HiltViewModel
class VideoCallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val videoRepository: VideoRepository,
    private val videoCallRepository: VideoCallRepository,
) : ViewModel() {

    val consultationId: String = savedStateHandle["consultationId"] ?: ""
    val callType: CallType = try {
        CallType.valueOf(savedStateHandle.get<String>("callType") ?: "VIDEO")
    } catch (_: IllegalArgumentException) {
        CallType.VIDEO
    }

    private val _uiState = MutableStateFlow(
        VideoCallUiState(
            callType = callType,
            isCameraEnabled = callType == CallType.VIDEO,
        ),
    )
    val uiState: StateFlow<VideoCallUiState> = _uiState.asStateFlow()

    var meeting: Meeting? = null
        private set

    private var timerJob: Job? = null
    private var callStartTimeMillis: Long = 0L

    fun onPermissionsGranted() {
        if (_uiState.value.callPhase == CallPhase.REQUESTING_PERMISSIONS) {
            fetchTokenAndJoin()
        }
    }

    fun onPermissionsDenied() {
        _uiState.update {
            it.copy(
                callPhase = CallPhase.ERROR,
                error = "Camera and microphone permissions are required for calls.",
            )
        }
    }

    private fun fetchTokenAndJoin() {
        if (consultationId.isBlank()) {
            Log.e(TAG, "fetchTokenAndJoin: consultationId is blank!")
            _uiState.update {
                it.copy(callPhase = CallPhase.ERROR, error = "No consultation ID provided.")
            }
            return
        }
        _uiState.update { it.copy(callPhase = CallPhase.CONNECTING) }
        viewModelScope.launch {
            Log.d(TAG, "Fetching video token for consultation=$consultationId")
            when (val result = videoRepository.getVideoToken(consultationId)) {
                is Result.Success -> {
                    val token = result.data
                    Log.d(TAG, "Got video token, roomId=${token.roomId}")
                    initializeMeeting(token.token, token.roomId)
                }
                is Result.Error -> {
                    val errorDetail = result.message ?: result.exception?.message ?: "Unknown error"
                    Log.e(TAG, "Failed to get video token: $errorDetail", result.exception)
                    _uiState.update {
                        it.copy(
                            callPhase = CallPhase.ERROR,
                            error = "Failed to connect: $errorDetail",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun initializeMeeting(token: String, roomId: String) {
        try {
            VideoSDK.config(token)
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            meeting = VideoSDK.initMeeting(
                appContext,
                roomId,
                "Participant",
                true,
                callType == CallType.VIDEO,
                null,
                "CONFERENCE",
                false,
                emptyMap<String, CustomStreamTrack>(),
                null,
                null as String?,
            )
            meeting?.addEventListener(createMeetingEventListener())
            meeting?.join()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize meeting", e)
            _uiState.update {
                it.copy(
                    callPhase = CallPhase.ERROR,
                    error = "Failed to start call: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    private fun createMeetingEventListener() = object : MeetingEventListener() {
        override fun onMeetingJoined() {
            Log.d(TAG, "Meeting joined")
            _uiState.update { it.copy(callPhase = CallPhase.WAITING_FOR_PARTICIPANT) }
        }

        override fun onMeetingLeft() {
            Log.d(TAG, "Meeting left")
            _uiState.update { it.copy(callPhase = CallPhase.ENDED) }
            saveCallRecord()
        }

        override fun onParticipantJoined(participant: Participant) {
            if (participant.isLocal) return
            Log.d(TAG, "Remote participant joined: ${participant.displayName}")
            callStartTimeMillis = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    callPhase = CallPhase.IN_CALL,
                    remoteParticipantJoined = true,
                    remoteParticipantName = participant.displayName,
                )
            }
            participant.addEventListener(createParticipantEventListener())
            startDurationTimer()
        }

        override fun onParticipantLeft(participant: Participant) {
            if (participant.isLocal) return
            Log.d(TAG, "Remote participant left: ${participant.displayName}")
            _uiState.update {
                it.copy(remoteParticipantJoined = false)
            }
            // End call when the other participant leaves
            endCall()
        }

        override fun onError(error: JSONObject) {
            Log.e(TAG, "Meeting error: $error")
            _uiState.update {
                it.copy(
                    callPhase = CallPhase.ERROR,
                    error = error.optString("message", "An error occurred during the call."),
                )
            }
        }
    }

    private fun createParticipantEventListener() = object : ParticipantEventListener() {
        override fun onStreamEnabled(stream: Stream) {
            // Stream state changes are tracked by the composable via meeting.participants
        }

        override fun onStreamDisabled(stream: Stream) {
            // Stream state changes are tracked by the composable via meeting.participants
        }
    }

    fun toggleMic() {
        val current = _uiState.value.isMicEnabled
        if (current) meeting?.muteMic() else meeting?.unmuteMic()
        _uiState.update { it.copy(isMicEnabled = !current) }
    }

    fun toggleCamera() {
        val current = _uiState.value.isCameraEnabled
        if (current) meeting?.disableWebcam() else meeting?.enableWebcam()
        _uiState.update { it.copy(isCameraEnabled = !current) }
    }

    fun switchCamera() {
        meeting?.changeWebcam()
    }

    fun endCall() {
        meeting?.leave()
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(TIMER_INTERVAL_MS)
                _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
            }
        }
    }

    private fun saveCallRecord() {
        timerJob?.cancel()
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val duration = _uiState.value.callDurationSeconds
                val startedAt = if (callStartTimeMillis > 0) callStartTimeMillis else now - (duration * 1000L)
                val call = VideoCall(
                    callId = UUID.randomUUID().toString(),
                    consultationId = consultationId,
                    startedAt = startedAt,
                    endedAt = now,
                    durationSeconds = duration,
                    callQuality = CallQuality.GOOD,
                    createdAt = now,
                )
                videoCallRepository.saveVideoCall(call)
                Log.d(TAG, "Saved call record: ${call.callId}, duration=${duration}s")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save call record", e)
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        meeting?.leave()
        super.onCleared()
    }

    companion object {
        private const val TAG = "VideoCallVM"
        private const val TIMER_INTERVAL_MS = 1000L
    }
}

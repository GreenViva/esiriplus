package com.esiri.esiriplus.feature.chat.viewmodel

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.CallQuality
import com.esiri.esiriplus.core.domain.model.CallType
import com.esiri.esiriplus.core.domain.model.VideoCall
import com.esiri.esiriplus.core.domain.model.VideoCallStatus
import com.esiri.esiriplus.core.domain.repository.CallRechargeRepository
import com.esiri.esiriplus.core.domain.repository.VideoCallRepository
import com.esiri.esiriplus.core.domain.repository.VideoRepository
import com.esiri.esiriplus.core.domain.service.CallServiceController
import com.esiri.esiriplus.feature.chat.R
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

enum class TimeWarning {
    NONE,
    LOW,      // ≤30 seconds remaining
    CRITICAL, // ≤10 seconds remaining
    EXPIRED,  // 0 seconds remaining
}

data class VideoCallUiState(
    val callPhase: CallPhase = CallPhase.REQUESTING_PERMISSIONS,
    val isMicEnabled: Boolean = true,
    val isCameraEnabled: Boolean = true,
    val isSpeakerOn: Boolean = true,
    val callDurationSeconds: Int = 0,
    val timeLimitSeconds: Int = 180,
    val remainingSeconds: Int = 180,
    val timeWarning: TimeWarning = TimeWarning.NONE,
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
    private val callServiceController: CallServiceController,
    private val callRechargeRepository: CallRechargeRepository,
) : ViewModel() {

    val consultationId: String = savedStateHandle["consultationId"] ?: ""
    private val navRoomId: String = savedStateHandle["roomId"] ?: ""
    val callType: CallType = try {
        CallType.valueOf(savedStateHandle.get<String>("callType") ?: "VIDEO")
    } catch (_: IllegalArgumentException) {
        CallType.VIDEO
    }

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val isVideoCall = callType == CallType.VIDEO

    private val _uiState = MutableStateFlow(
        VideoCallUiState(
            callType = callType,
            isCameraEnabled = isVideoCall,
            isSpeakerOn = isVideoCall, // speaker on for video, earpiece for audio
        ),
    )
    val uiState: StateFlow<VideoCallUiState> = _uiState.asStateFlow()

    var meeting: Meeting? = null
        private set

    private var timerJob: Job? = null
    private var waitingTimeoutJob: Job? = null
    private var joinTimeoutJob: Job? = null
    private var callStartTimeMillis: Long = 0L
    private var meetingRoomId: String = ""
    private var isTimeExpired: Boolean = false

    fun onPermissionsGranted() {
        if (_uiState.value.callPhase == CallPhase.REQUESTING_PERMISSIONS) {
            fetchTokenAndJoin()
        }
    }

    fun onPermissionsDenied() {
        _uiState.update {
            it.copy(
                callPhase = CallPhase.ERROR,
                error = appContext.getString(R.string.video_call_error_permissions),
            )
        }
    }

    private fun fetchTokenAndJoin() {
        if (consultationId.isBlank()) {
            Log.e(TAG, "fetchTokenAndJoin: consultationId is blank!")
            _uiState.update {
                it.copy(
                    callPhase = CallPhase.ERROR,
                    error = appContext.getString(R.string.video_call_error_no_consultation),
                )
            }
            return
        }
        _uiState.update { it.copy(callPhase = CallPhase.CONNECTING) }
        viewModelScope.launch {
            val roomIdParam = navRoomId.ifBlank { null }
            Log.d(TAG, "Fetching video token for consultation=$consultationId callType=$callType roomId=$roomIdParam")
            when (val result = videoRepository.getVideoToken(consultationId, callType.name, roomIdParam)) {
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
                            error = appContext.getString(R.string.video_call_error_connect, errorDetail),
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun initializeMeeting(token: String, roomId: String) {
        meetingRoomId = roomId
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
            startJoinTimeout()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize meeting", e)
            _uiState.update {
                it.copy(
                    callPhase = CallPhase.ERROR,
                    error = appContext.getString(
                        R.string.video_call_error_start,
                        e.message ?: e.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    private fun createMeetingEventListener() = object : MeetingEventListener() {
        override fun onMeetingJoined() {
            Log.d(TAG, "Meeting joined")
            joinTimeoutJob?.cancel()
            _uiState.update { it.copy(callPhase = CallPhase.WAITING_FOR_PARTICIPANT) }
            startWaitingTimeout()
        }

        override fun onMeetingLeft() {
            Log.d(TAG, "Meeting left")
            callServiceController.stopCallService()
            _uiState.update { it.copy(callPhase = CallPhase.ENDED) }
            saveCallRecord()
        }

        override fun onParticipantJoined(participant: Participant) {
            if (participant.isLocal) return
            Log.d(TAG, "Remote participant joined: ${participant.displayName}")
            waitingTimeoutJob?.cancel()
            callStartTimeMillis = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    callPhase = CallPhase.IN_CALL,
                    remoteParticipantJoined = true,
                    remoteParticipantName = participant.displayName,
                )
            }
            participant.addEventListener(createParticipantEventListener())
            // Start foreground service to prevent OS from killing the call
            callServiceController.startCallService(consultationId, callType.name, isVideoCall)
            // Set default audio routing: speaker for video, earpiece for audio
            setAudioRouting(isVideoCall)
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
            callServiceController.stopCallService()
            _uiState.update {
                it.copy(
                    callPhase = CallPhase.ERROR,
                    error = error.optString(
                        "message",
                        appContext.getString(R.string.video_call_error_generic),
                    ),
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

    fun toggleSpeaker() {
        val newValue = !_uiState.value.isSpeakerOn
        audioManager.isSpeakerphoneOn = newValue
        _uiState.update { it.copy(isSpeakerOn = newValue) }
    }

    fun endCall() {
        meeting?.leave()
    }

    fun addCallMinutes(minutes: Int) {
        val additionalSeconds = minutes * 60
        _uiState.update {
            val newRemaining = it.remainingSeconds + additionalSeconds
            it.copy(
                timeLimitSeconds = it.timeLimitSeconds + additionalSeconds,
                remainingSeconds = newRemaining,
                timeWarning = computeTimeWarning(newRemaining),
            )
        }
        Log.d(TAG, "Added $minutes minutes. New limit=${_uiState.value.timeLimitSeconds}s, remaining=${_uiState.value.remainingSeconds}s")
    }

    fun submitRecharge(minutes: Int, phoneNumber: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = callRechargeRepository.submitRecharge(consultationId, minutes, phoneNumber)
            if (success) {
                addCallMinutes(minutes)
            }
            onResult(success)
        }
    }

    private fun setAudioRouting(speakerOn: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerOn
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set audio routing", e)
        }
    }

    private fun startJoinTimeout() {
        joinTimeoutJob?.cancel()
        joinTimeoutJob = viewModelScope.launch {
            delay(JOIN_TIMEOUT_MS)
            if (_uiState.value.callPhase == CallPhase.CONNECTING) {
                Log.e(TAG, "Join timeout — meeting did not connect within ${JOIN_TIMEOUT_MS / 1000}s")
                _uiState.update {
                    it.copy(
                        callPhase = CallPhase.ERROR,
                        error = appContext.getString(R.string.video_call_error_connect_timeout),
                    )
                }
                meeting?.leave()
            }
        }
    }

    private fun startWaitingTimeout() {
        waitingTimeoutJob?.cancel()
        waitingTimeoutJob = viewModelScope.launch {
            delay(WAITING_TIMEOUT_MS)
            if (_uiState.value.callPhase == CallPhase.WAITING_FOR_PARTICIPANT) {
                Log.d(TAG, "Waiting timeout — no answer")
                _uiState.update {
                    it.copy(
                        callPhase = CallPhase.ENDED,
                        error = appContext.getString(R.string.video_call_no_answer),
                    )
                }
                meeting?.leave()
            }
        }
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(TIMER_INTERVAL_MS)
                _uiState.update { state ->
                    val newDuration = state.callDurationSeconds + 1
                    val newRemaining = (state.remainingSeconds - 1).coerceAtLeast(0)
                    val warning = computeTimeWarning(newRemaining)
                    state.copy(
                        callDurationSeconds = newDuration,
                        remainingSeconds = newRemaining,
                        timeWarning = warning,
                    )
                }
                // Update foreground service notification
                callServiceController.updateCallDuration(_uiState.value.callDurationSeconds)

                // Auto-end call when time expires
                if (_uiState.value.remainingSeconds <= 0 && _uiState.value.timeWarning == TimeWarning.EXPIRED) {
                    Log.d(TAG, "Call time expired, ending call")
                    isTimeExpired = true
                    endCall()
                }
            }
        }
    }

    private fun computeTimeWarning(remainingSeconds: Int): TimeWarning = when {
        remainingSeconds <= 0 -> TimeWarning.EXPIRED
        remainingSeconds <= 10 -> TimeWarning.CRITICAL
        remainingSeconds <= 30 -> TimeWarning.LOW
        else -> TimeWarning.NONE
    }

    private fun saveCallRecord() {
        timerJob?.cancel()
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val state = _uiState.value
                val duration = state.callDurationSeconds
                val startedAt = if (callStartTimeMillis > 0) callStartTimeMillis else now - (duration * 1000L)
                val timeUsed = state.timeLimitSeconds - state.remainingSeconds
                val call = VideoCall(
                    callId = UUID.randomUUID().toString(),
                    consultationId = consultationId,
                    startedAt = startedAt,
                    endedAt = now,
                    durationSeconds = duration,
                    callQuality = CallQuality.GOOD,
                    createdAt = now,
                    meetingId = meetingRoomId,
                    initiatedBy = "",
                    callType = callType.name,
                    status = if (duration > 0) VideoCallStatus.COMPLETED else VideoCallStatus.MISSED,
                    timeLimitSeconds = state.timeLimitSeconds,
                    timeUsedSeconds = timeUsed,
                    isTimeExpired = isTimeExpired,
                    totalRecharges = 0,
                )
                videoCallRepository.saveVideoCall(call)
                Log.d(TAG, "Saved call record: ${call.callId}, duration=${duration}s, timeUsed=${timeUsed}s, expired=$isTimeExpired")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save call record", e)
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        waitingTimeoutJob?.cancel()
        joinTimeoutJob?.cancel()
        callServiceController.stopCallService()
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (_: Exception) { /* best effort */ }
        meeting?.leave()
        super.onCleared()
    }

    companion object {
        private const val TAG = "VideoCallVM"
        private const val TIMER_INTERVAL_MS = 1000L
        private const val WAITING_TIMEOUT_MS = 60_000L
        private const val JOIN_TIMEOUT_MS = 30_000L
    }
}

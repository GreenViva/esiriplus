package com.esiri.esiriplus.feature.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.CallType
import com.esiri.esiriplus.feature.chat.R
import com.esiri.esiriplus.feature.chat.viewmodel.CallPhase
import com.esiri.esiriplus.feature.chat.viewmodel.TimeWarning
import com.esiri.esiriplus.feature.chat.viewmodel.VideoCallUiState
import com.esiri.esiriplus.feature.chat.viewmodel.VideoCallViewModel
import android.util.Log
import kotlinx.coroutines.delay
import live.videosdk.rtc.android.Stream
import live.videosdk.rtc.android.VideoView
import live.videosdk.rtc.android.listeners.ParticipantEventListener
import org.webrtc.VideoTrack

private val BrandTeal = Color(0xFF2A9D8F)
private val WarningAmber = Color(0xFFF59E0B)
private val WarningRed = Color(0xFFEF4444)

@Composable
fun VideoCallScreen(
    onCallEnded: () -> Unit,
    modifier: Modifier = Modifier,
    userType: String = "",
    viewModel: VideoCallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showRechargeModal by remember { mutableStateOf(false) }

    // Keep screen awake during call
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Permission handling
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) viewModel.onPermissionsGranted()
        else viewModel.onPermissionsDenied()
    }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.CAMERA)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.isEmpty()) {
            viewModel.onPermissionsGranted()
        } else {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    // Navigate back when call ends
    LaunchedEffect(uiState.callPhase) {
        if (uiState.callPhase == CallPhase.ENDED) {
            delay(1500L)
            onCallEnded()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
    ) {
        when (uiState.callPhase) {
            CallPhase.REQUESTING_PERMISSIONS -> ConnectingOverlay(stringResource(R.string.video_call_requesting_permissions))
            CallPhase.CONNECTING -> ConnectingOverlay(stringResource(R.string.video_call_connecting))
            CallPhase.WAITING_FOR_PARTICIPANT -> WaitingOverlay(uiState)
            CallPhase.IN_CALL -> {
                if (uiState.callType == CallType.VIDEO) {
                    VideoCallContent(uiState, viewModel)
                } else {
                    AudioCallContent(uiState)
                }
            }
            CallPhase.ENDED -> CallEndedOverlay(uiState)
            CallPhase.ERROR -> ErrorOverlay(uiState.error, onCallEnded)
        }

        // Bottom control bar
        if (uiState.callPhase in listOf(
                CallPhase.WAITING_FOR_PARTICIPANT,
                CallPhase.IN_CALL,
            )
        ) {
            CallControlBar(
                uiState = uiState,
                onToggleMic = viewModel::toggleMic,
                onToggleCamera = viewModel::toggleCamera,
                onSwitchCamera = viewModel::switchCamera,
                onToggleSpeaker = viewModel::toggleSpeaker,
                onEndCall = viewModel::endCall,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // "Add Time" button for patients when time is running low
        if (uiState.callPhase == CallPhase.IN_CALL &&
            userType.equals("patient", ignoreCase = true) &&
            uiState.timeWarning >= TimeWarning.LOW
        ) {
            TextButton(
                onClick = { showRechargeModal = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp)
                    .background(BrandTeal, RoundedCornerShape(20.dp)),
            ) {
                Text(
                    text = stringResource(R.string.video_call_add_time),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    // Call recharge modal
    if (showRechargeModal) {
        CallRechargeModal(
            consultationId = viewModel.consultationId,
            onDismiss = { showRechargeModal = false },
            onRechargeSuccess = { showRechargeModal = false },
            onSubmitRecharge = viewModel::submitRecharge,
        )
    }
}

@Composable
private fun ConnectingOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = BrandTeal)
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun WaitingOverlay(uiState: VideoCallUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .background(BrandTeal.copy(alpha = 0.2f), CircleShape)
                    .padding(20.dp),
                tint = BrandTeal,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.video_call_waiting_participant),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            val callLabel = if (uiState.callType == CallType.VIDEO) {
                stringResource(R.string.video_call_label)
            } else {
                stringResource(R.string.video_call_voice_label)
            }
            Text(
                text = callLabel,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun VideoCallContent(uiState: VideoCallUiState, viewModel: VideoCallViewModel) {
    val meeting = viewModel.meeting ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        // Remote participant video (fullscreen)
        val remoteParticipant = meeting.participants.values.firstOrNull()
        if (remoteParticipant != null) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).also { videoView ->
                        val participant = meeting.participants.values.firstOrNull() ?: return@also
                        Log.d("VideoCallScreen", "Remote VideoView created for ${participant.id}, streams=${participant.streams.size}")

                        // Add existing video track if already streaming
                        participant.streams.values
                            .firstOrNull { it.kind.equals("video", ignoreCase = true) }
                            ?.let { stream ->
                                Log.d("VideoCallScreen", "Remote: adding existing video track")
                                (stream.track as? VideoTrack)?.let { videoView.addTrack(it) }
                            }

                        // Listen for future stream changes
                        participant.addEventListener(object : ParticipantEventListener() {
                            override fun onStreamEnabled(stream: Stream) {
                                if (stream.kind.equals("video", ignoreCase = true)) {
                                    Log.d("VideoCallScreen", "Remote: stream enabled, adding track")
                                    (stream.track as? VideoTrack)?.let { videoView.addTrack(it) }
                                }
                            }
                            override fun onStreamDisabled(stream: Stream) {
                                if (stream.kind.equals("video", ignoreCase = true)) {
                                    Log.d("VideoCallScreen", "Remote: stream disabled, removing track")
                                    videoView.removeTrack()
                                }
                            }
                        })
                    }
                },
                onRelease = { it.releaseSurfaceViewRenderer() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // No remote video — show avatar
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .background(BrandTeal.copy(alpha = 0.2f), CircleShape)
                        .padding(24.dp),
                    tint = BrandTeal,
                )
            }
        }

        // Local participant video (PIP in top-right)
        if (uiState.isCameraEnabled) {
            val localParticipant = meeting.localParticipant
            if (localParticipant != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).also { videoView ->
                            videoView.setMirror(true)
                            val participant = meeting.localParticipant ?: return@also
                            Log.d("VideoCallScreen", "Local VideoView created, streams=${participant.streams.size}")

                            // Add existing video track if already streaming
                            participant.streams.values
                                .firstOrNull { it.kind.equals("video", ignoreCase = true) }
                                ?.let { stream ->
                                    Log.d("VideoCallScreen", "Local: adding existing video track")
                                    (stream.track as? VideoTrack)?.let { videoView.addTrack(it) }
                                }

                            // Listen for future stream changes
                            participant.addEventListener(object : ParticipantEventListener() {
                                override fun onStreamEnabled(stream: Stream) {
                                    if (stream.kind.equals("video", ignoreCase = true)) {
                                        Log.d("VideoCallScreen", "Local: stream enabled, adding track")
                                        (stream.track as? VideoTrack)?.let { videoView.addTrack(it) }
                                    }
                                }
                                override fun onStreamDisabled(stream: Stream) {
                                    if (stream.kind.equals("video", ignoreCase = true)) {
                                        Log.d("VideoCallScreen", "Local: stream disabled, removing track")
                                        videoView.removeTrack()
                                    }
                                }
                            })
                        }
                    },
                    onRelease = { it.releaseSurfaceViewRenderer() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(width = 120.dp, height = 160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }

        // Countdown timer pill
        CountdownTimerPill(
            remainingSeconds = uiState.remainingSeconds,
            timeWarning = uiState.timeWarning,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        )
    }
}

@Composable
private fun AudioCallContent(uiState: VideoCallUiState) {
    val participantFallback = stringResource(R.string.video_call_participant)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .background(BrandTeal.copy(alpha = 0.2f), CircleShape)
                .padding(24.dp),
            tint = BrandTeal,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = uiState.remoteParticipantName ?: participantFallback,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))
        CountdownTimerPill(
            remainingSeconds = uiState.remainingSeconds,
            timeWarning = uiState.timeWarning,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.video_call_voice_label),
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CallEndedOverlay(uiState: VideoCallUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.video_call_ended),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatDuration(uiState.callDurationSeconds),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ErrorOverlay(error: String?, onBack: () -> Unit) {
    val fallbackError = stringResource(R.string.video_call_something_wrong)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = error ?: fallbackError,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.video_call_go_back), color = BrandTeal)
            }
        }
    }
}

@Composable
private fun CallControlBar(
    uiState: VideoCallUiState,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muteLabel = stringResource(R.string.video_call_mute)
    val unmuteLabel = stringResource(R.string.video_call_unmute)
    val camOffLabel = stringResource(R.string.video_call_cam_off)
    val camOnLabel = stringResource(R.string.video_call_cam_on)
    val flipLabel = stringResource(R.string.video_call_flip)
    val speakerLabel = stringResource(R.string.video_call_speaker)
    val earpieceLabel = stringResource(R.string.video_call_earpiece)
    val endLabel = stringResource(R.string.video_call_end)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 40.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mic toggle
        CallControlButton(
            icon = if (uiState.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            label = if (uiState.isMicEnabled) muteLabel else unmuteLabel,
            isActive = uiState.isMicEnabled,
            onClick = onToggleMic,
        )

        if (uiState.callType == CallType.VIDEO) {
            // Camera toggle
            CallControlButton(
                icon = if (uiState.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = if (uiState.isCameraEnabled) camOffLabel else camOnLabel,
                isActive = uiState.isCameraEnabled,
                onClick = onToggleCamera,
            )
            // Flip camera
            CallControlButton(
                icon = Icons.Default.FlipCameraAndroid,
                label = flipLabel,
                isActive = true,
                onClick = onSwitchCamera,
            )
        }

        // Speaker toggle
        CallControlButton(
            icon = if (uiState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            label = if (uiState.isSpeakerOn) speakerLabel else earpieceLabel,
            isActive = uiState.isSpeakerOn,
            onClick = onToggleSpeaker,
        )

        // End call
        CallControlButton(
            icon = Icons.Default.CallEnd,
            label = endLabel,
            isActive = false,
            backgroundColor = Color(0xFFDC2626),
            onClick = onEndCall,
        )
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = true,
    backgroundColor: Color? = null,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    backgroundColor ?: if (isActive) Color.White.copy(alpha = 0.2f)
                    else Color.White.copy(alpha = 0.1f),
                    CircleShape,
                ),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CountdownTimerPill(
    remainingSeconds: Int,
    timeWarning: TimeWarning,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when (timeWarning) {
        TimeWarning.NONE -> Color.Black.copy(alpha = 0.5f)
        TimeWarning.LOW -> WarningAmber
        TimeWarning.CRITICAL -> WarningRed
        TimeWarning.EXPIRED -> WarningRed
    }

    // Pulse animation for CRITICAL warning
    val alpha = if (timeWarning == TimeWarning.CRITICAL) {
        val infiniteTransition = rememberInfiniteTransition(label = "timerPulse")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        animatedAlpha
    } else {
        1f
    }

    val text = if (timeWarning == TimeWarning.EXPIRED) {
        stringResource(R.string.video_call_time_expired)
    } else {
        formatDuration(remainingSeconds)
    }

    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .alpha(alpha)
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

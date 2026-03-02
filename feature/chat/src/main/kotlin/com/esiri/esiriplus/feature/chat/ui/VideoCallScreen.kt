package com.esiri.esiriplus.feature.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.CallType
import com.esiri.esiriplus.feature.chat.viewmodel.CallPhase
import com.esiri.esiriplus.feature.chat.viewmodel.VideoCallUiState
import com.esiri.esiriplus.feature.chat.viewmodel.VideoCallViewModel
import kotlinx.coroutines.delay
import live.videosdk.rtc.android.VideoView
import org.webrtc.VideoTrack

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun VideoCallScreen(
    onCallEnded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoCallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
            CallPhase.REQUESTING_PERMISSIONS -> ConnectingOverlay("Requesting permissions...")
            CallPhase.CONNECTING -> ConnectingOverlay("Connecting...")
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
                onEndCall = viewModel::endCall,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
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
                text = "Waiting for participant to join...",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            val callLabel = if (uiState.callType == CallType.VIDEO) "Video Call" else "Voice Call"
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
                    VideoView(ctx).apply {
                        val videoStream = remoteParticipant.streams.values
                            .firstOrNull { it.kind.equals("video", ignoreCase = true) }
                        (videoStream?.track as? VideoTrack)?.let { addTrack(it) }
                    }
                },
                update = { videoView ->
                    val videoStream = remoteParticipant.streams.values
                        .firstOrNull { it.kind.equals("video", ignoreCase = true) }
                    val track = videoStream?.track as? VideoTrack
                    if (track != null) {
                        videoView.addTrack(track)
                    } else {
                        videoView.removeTrack()
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
                        VideoView(ctx).apply {
                            setMirror(true)
                            val videoStream = localParticipant.streams.values
                                .firstOrNull { it.kind.equals("video", ignoreCase = true) }
                            (videoStream?.track as? VideoTrack)?.let { addTrack(it) }
                        }
                    },
                    update = { videoView ->
                        val videoStream = localParticipant.streams.values
                            .firstOrNull { it.kind.equals("video", ignoreCase = true) }
                        val track = videoStream?.track as? VideoTrack
                        if (track != null) {
                            videoView.addTrack(track)
                        } else {
                            videoView.removeTrack()
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

        // Duration overlay
        Text(
            text = formatDuration(uiState.callDurationSeconds),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun AudioCallContent(uiState: VideoCallUiState) {
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
            text = uiState.remoteParticipantName ?: "Participant",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatDuration(uiState.callDurationSeconds),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Voice Call",
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
                text = "Call Ended",
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = error ?: "Something went wrong",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) {
                Text("Go Back", color = BrandTeal)
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
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            label = if (uiState.isMicEnabled) "Mute" else "Unmute",
            isActive = uiState.isMicEnabled,
            onClick = onToggleMic,
        )

        if (uiState.callType == CallType.VIDEO) {
            // Camera toggle
            CallControlButton(
                icon = if (uiState.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = if (uiState.isCameraEnabled) "Cam Off" else "Cam On",
                isActive = uiState.isCameraEnabled,
                onClick = onToggleCamera,
            )
            // Flip camera
            CallControlButton(
                icon = Icons.Default.FlipCameraAndroid,
                label = "Flip",
                isActive = true,
                onClick = onSwitchCamera,
            )
        }

        // End call
        CallControlButton(
            icon = Icons.Default.CallEnd,
            label = "End",
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

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

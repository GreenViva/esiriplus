package com.esiri.esiriplus.feature.doctor.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.esiri.esiriplus.feature.chat.ui.VideoCallScreen

@Composable
fun DoctorVideoCallScreen(
    onCallEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoCallScreen(
        onCallEnded = onCallEnded,
        modifier = modifier,
    )
}

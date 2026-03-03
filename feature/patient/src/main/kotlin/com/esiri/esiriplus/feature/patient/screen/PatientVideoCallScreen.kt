package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.esiri.esiriplus.feature.chat.ui.VideoCallScreen

@Composable
fun PatientVideoCallScreen(
    onCallEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    VideoCallScreen(
        onCallEnded = onCallEnded,
        modifier = modifier,
        userType = "patient",
    )
}

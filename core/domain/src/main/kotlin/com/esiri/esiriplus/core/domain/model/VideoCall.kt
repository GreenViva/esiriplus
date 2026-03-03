package com.esiri.esiriplus.core.domain.model

data class VideoCall(
    val callId: String,
    val consultationId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSeconds: Int,
    val callQuality: CallQuality,
    val createdAt: Long,
    val meetingId: String = "",
    val initiatedBy: String = "",
    val callType: String = "VIDEO",
    val status: VideoCallStatus = VideoCallStatus.INITIATED,
    val timeLimitSeconds: Int = 180,
    val timeUsedSeconds: Int = 0,
    val isTimeExpired: Boolean = false,
    val totalRecharges: Int = 0,
)

enum class CallQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
}

enum class VideoCallStatus {
    INITIATED,
    RINGING,
    CONNECTED,
    COMPLETED,
    MISSED,
    DECLINED,
    FAILED,
}

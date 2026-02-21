package com.esiri.esiriplus.core.domain.model

data class VideoCall(
    val callId: String,
    val consultationId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSeconds: Int,
    val callQuality: CallQuality,
    val createdAt: Long,
)

enum class CallQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
}

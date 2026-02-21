package com.esiri.esiriplus.core.domain.model

data class Notification(
    val notificationId: String,
    val userId: String,
    val title: String,
    val body: String,
    val type: NotificationType,
    val data: String,
    val readAt: Long? = null,
    val createdAt: Long,
)

enum class NotificationType {
    CONSULTATION_REQUEST,
    MESSAGE_RECEIVED,
    VIDEO_CALL_INCOMING,
    REPORT_READY,
    PAYMENT_STATUS,
}

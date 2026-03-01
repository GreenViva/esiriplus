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
    CONSULTATION_ACCEPTED,
    MESSAGE_RECEIVED,
    VIDEO_CALL_INCOMING,
    REPORT_READY,
    PAYMENT_STATUS,
    DOCTOR_APPROVED,
    DOCTOR_REJECTED,
    DOCTOR_WARNED,
    DOCTOR_SUSPENDED,
    DOCTOR_UNSUSPENDED,
    DOCTOR_BANNED,
    DOCTOR_UNBANNED,
    ;

    companion object {
        fun fromString(value: String): NotificationType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: CONSULTATION_REQUEST
    }
}

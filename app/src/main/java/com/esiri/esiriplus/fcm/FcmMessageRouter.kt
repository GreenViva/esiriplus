package com.esiri.esiriplus.fcm

/**
 * Determines how an incoming FCM message should be handled based on
 * the message type and current service state.
 *
 * Extracted from [EsiriplusFirebaseMessagingService] for testability.
 */
object FcmMessageRouter {

    enum class Route {
        SKIP_DUPLICATE,
        CONSULTATION_REQUEST_FALLBACK,
        INCOMING_CALL,
        SECURE_FETCH,
        INLINE_NOTIFICATION,
        DROP,
    }

    fun route(
        type: String,
        notificationId: String?,
        hasTitle: Boolean,
        isDoctorOnlineServiceRunning: Boolean,
    ): Route = when {
        type.equals("CONSULTATION_REQUEST", ignoreCase = true) && isDoctorOnlineServiceRunning ->
            Route.SKIP_DUPLICATE

        type.equals("CONSULTATION_REQUEST", ignoreCase = true) && !isDoctorOnlineServiceRunning ->
            Route.CONSULTATION_REQUEST_FALLBACK

        type.equals("VIDEO_CALL_INCOMING", ignoreCase = true) ->
            Route.INCOMING_CALL

        notificationId != null ->
            Route.SECURE_FETCH

        hasTitle ->
            Route.INLINE_NOTIFICATION

        else ->
            Route.DROP
    }

    fun genericTitleKey(type: String): String = when (type.uppercase()) {
        "CONSULTATION_REQUEST" -> "notification_new_consultation"
        "CONSULTATION_ACCEPTED" -> "notification_consultation_accepted"
        "MESSAGE_RECEIVED" -> "notification_new_message"
        "VIDEO_CALL_INCOMING" -> "notification_incoming_video_call"
        "REPORT_READY" -> "notification_report_ready"
        "PAYMENT_STATUS" -> "notification_payment_update"
        "DOCTOR_APPROVED", "DOCTOR_REJECTED" -> "notification_application_update"
        "DOCTOR_WARNED" -> "notification_admin_warning"
        "DOCTOR_SUSPENDED" -> "notification_account_suspended"
        "DOCTOR_UNSUSPENDED", "DOCTOR_UNBANNED" -> "notification_account_reinstated"
        "DOCTOR_BANNED" -> "notification_account_banned"
        else -> "notification_default_title"
    }
}

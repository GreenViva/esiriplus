package com.esiri.esiriplus.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.esiri.esiriplus.EsiriplusApp
import com.esiri.esiriplus.MainActivity
import com.esiri.esiriplus.R
import com.esiri.esiriplus.call.IncomingCall
import com.esiri.esiriplus.call.IncomingCallStateHolder
import com.esiri.esiriplus.core.common.locale.LocaleHelper
import com.esiri.esiriplus.service.DoctorOnlineService
import com.esiri.esiriplus.service.overlay.OverlayBubbleManager
import com.esiri.esiriplus.core.database.dao.NotificationDao
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import com.esiri.esiriplus.core.domain.repository.NotificationRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.service.ConsultationRequestRealtimeService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Firebase Cloud Messaging service for eSIRI+.
 *
 * Medical privacy: push payload contains no patient data.
 * Only notification_id and type are included in the FCM data payload.
 * Full notification content is fetched securely from Supabase after wake.
 */
@AndroidEntryPoint
class EsiriplusFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationDao: NotificationDao

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var incomingCallStateHolder: IncomingCallStateHolder

    @Inject
    lateinit var edgeFunctionClient: EdgeFunctionClient

    @Inject
    lateinit var consultationRequestRealtimeService: ConsultationRequestRealtimeService

    @Inject
    lateinit var userPreferencesManager: com.esiri.esiriplus.core.common.preferences.UserPreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Locale-aware context for resolving string resources in this service. */
    private val localizedContext: Context
        get() = LocaleHelper.getLocalizedContext(this)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val notificationId = remoteMessage.data["notification_id"]
        val type = remoteMessage.data["type"] ?: "GENERAL"

        // Consultation request — always emit to IncomingRequestViewModel so the dialog
        // shows reliably even when Realtime is slow/unauthenticated.
        if (type.equals("CONSULTATION_REQUEST", ignoreCase = true)) {
            val requestId = remoteMessage.data["request_id"] ?: notificationId ?: ""
            val serviceType = remoteMessage.data["service_type"]
            Log.d(TAG, "CONSULTATION_REQUEST FCM: requestId=$requestId serviceRunning=${DoctorOnlineService.isRunning}")
            // Emit to ViewModel so the in-app dialog appears (deduplicated by requestId).
            consultationRequestRealtimeService.emitExternalRequest(requestId, serviceType)
            // Also show system notification so the doctor is alerted if the app is backgrounded.
            if (!DoctorOnlineService.isRunning) {
                // Service not running — show high-priority fallback notification
                showConsultationRequestFallback(requestId)
            }
            return
        }

        // Incoming video/voice call — show full-screen call notification
        if (type.equals("VIDEO_CALL_INCOMING", ignoreCase = true)) {
            val consultationId = remoteMessage.data["consultation_id"] ?: ""
            val roomId = remoteMessage.data["room_id"] ?: ""
            val callType = remoteMessage.data["call_type"] ?: "VIDEO"
            val callerRole = remoteMessage.data["caller_role"] ?: "doctor"
            Log.d(TAG, "Incoming call: consultation=$consultationId room=$roomId type=$callType caller=$callerRole allData=${remoteMessage.data}")

            incomingCallStateHolder.showIncomingCall(
                IncomingCall(
                    consultationId = consultationId,
                    roomId = roomId,
                    callType = callType,
                    callerRole = callerRole,
                ),
            )
            showIncomingCallNotification(consultationId, callType, callerRole, roomId)
            return
        }

        if (notificationId != null) {
            // Privacy-first: fetch full content securely from Supabase
            handleSecureFetch(notificationId, type)
        } else {
            // Fallback: use inline notification data (e.g., from admin panel direct push)
            handleInlineNotification(remoteMessage)
        }
    }

    /**
     * Privacy-first path: FCM payload only contains notification_id + type.
     * We fetch the full notification content from Supabase via authenticated API.
     */
    private fun handleSecureFetch(notificationId: String, type: String) {
        val ctx = localizedContext
        // Show a generic notification immediately so user sees something
        showNotification(
            title = getGenericTitle(ctx, type),
            body = ctx.getString(R.string.notification_tap_to_view),
            notificationId = notificationId,
        )

        // Fetch full content from Supabase and update Room
        serviceScope.launch {
            try {
                notificationRepository.fetchAndStoreNotification(notificationId)
                // Update the system notification with real content
                val stored = notificationDao.getById(notificationId)
                if (stored != null) {
                    showNotification(
                        title = stored.title,
                        body = stored.body,
                        notificationId = notificationId,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch notification from Supabase", e)
            }
        }
    }

    /**
     * Fallback path: notification data is included in the push payload.
     * Used for direct admin pushes (approve/reject) where the Edge Function
     * sends title/body inline.
     */
    private fun handleInlineNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: return
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""
        val type = remoteMessage.data["type"] ?: "GENERAL"
        val userId = remoteMessage.data["user_id"] ?: ""
        val notificationId = UUID.randomUUID().toString()

        // Persist to Room
        serviceScope.launch {
            try {
                val entity = NotificationEntity(
                    notificationId = notificationId,
                    userId = userId,
                    title = title,
                    body = body,
                    type = type.uppercase(),
                    data = "{}",
                    createdAt = System.currentTimeMillis(),
                )
                notificationDao.insert(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist notification", e)
            }
        }

        showNotification(title = title, body = body, notificationId = notificationId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received, pushing to server")
        serviceScope.launch {
            try {
                val body = kotlinx.serialization.json.buildJsonObject {
                    put("fcm_token", kotlinx.serialization.json.JsonPrimitive(token))
                }
                val result = edgeFunctionClient.invoke("update-fcm-token", body)
                Log.d(TAG, "FCM token push result: $result")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push new FCM token to server", e)
            }
        }
    }

    private fun showConsultationRequestFallback(requestId: String) {
        val ctx = localizedContext
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(OverlayBubbleManager.EXTRA_ACTION, OverlayBubbleManager.ACTION_INCOMING_REQUEST)
            putExtra(OverlayBubbleManager.EXTRA_REQUEST_ID, requestId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestId.hashCode(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, EsiriplusApp.CHANNEL_INCOMING_REQUEST)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(R.string.notification_new_consultation))
            .setContentText(ctx.getString(R.string.notification_patient_waiting))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(
                requestId.hashCode(),
                notification,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted", e)
        }
    }

    private fun showIncomingCallNotification(
        consultationId: String,
        callType: String,
        callerRole: String,
        roomId: String,
    ) {
        val ctx = localizedContext
        val callLabel = if (callType == "AUDIO") ctx.getString(R.string.call_type_voice) else ctx.getString(R.string.call_type_video)
        val callerLabel = if (callerRole == "doctor") ctx.getString(R.string.caller_your_doctor) else ctx.getString(R.string.caller_your_patient)

        // Accept action → open MainActivity with accept_call action
        val acceptIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_ACCEPT_CALL
            putExtra(EXTRA_CONSULTATION_ID, consultationId)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_ROOM_ID, roomId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val acceptPending = PendingIntent.getActivity(
            this,
            CALL_NOTIFICATION_ID,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Decline action → broadcast to CallDeclineBroadcastReceiver
        val declineIntent = Intent(this, CallDeclineBroadcastReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
        }
        val declinePending = PendingIntent.getBroadcast(
            this,
            CALL_NOTIFICATION_ID + 1,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, EsiriplusApp.CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(R.string.call_incoming_title, callLabel))
            .setContentText(ctx.getString(R.string.call_incoming_body, callerLabel))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(60_000)
            .setFullScreenIntent(acceptPending, true)
            .addAction(0, ctx.getString(R.string.action_accept), acceptPending)
            .addAction(0, ctx.getString(R.string.action_decline), declinePending)

        // Use custom call ringtone if set
        val customCallUri = userPreferencesManager.callRingtoneUri.value
        if (customCallUri != null) {
            builder.setSound(customCallUri)
        }

        val notification = builder.build()

        try {
            NotificationManagerCompat.from(this).notify(CALL_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted", e)
        }
    }

    private fun showNotification(title: String, body: String, notificationId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_id", notificationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId.hashCode(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, EsiriplusApp.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(
                notificationId.hashCode(),
                notification,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted", e)
        }
    }

    private fun getGenericTitle(ctx: Context, type: String): String = when (type.uppercase()) {
        "CONSULTATION_REQUEST" -> ctx.getString(R.string.notification_new_consultation)
        "CONSULTATION_ACCEPTED" -> ctx.getString(R.string.notification_consultation_accepted)
        "MESSAGE_RECEIVED" -> ctx.getString(R.string.notification_new_message)
        "VIDEO_CALL_INCOMING" -> ctx.getString(R.string.notification_incoming_video_call)
        "REPORT_READY" -> ctx.getString(R.string.notification_report_ready)
        "PAYMENT_STATUS" -> ctx.getString(R.string.notification_payment_update)
        "DOCTOR_APPROVED" -> ctx.getString(R.string.notification_application_update)
        "DOCTOR_REJECTED" -> ctx.getString(R.string.notification_application_update)
        "DOCTOR_WARNED" -> ctx.getString(R.string.notification_admin_warning)
        "DOCTOR_SUSPENDED" -> ctx.getString(R.string.notification_account_suspended)
        "DOCTOR_UNSUSPENDED" -> ctx.getString(R.string.notification_account_reinstated)
        "DOCTOR_BANNED" -> ctx.getString(R.string.notification_account_banned)
        "DOCTOR_UNBANNED" -> ctx.getString(R.string.notification_account_reinstated)
        else -> ctx.getString(R.string.notification_default_title)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EsiriplusFCM"
        const val ACTION_ACCEPT_CALL = "com.esiri.esiriplus.ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.esiri.esiriplus.DECLINE_CALL"
        const val EXTRA_CONSULTATION_ID = "consultation_id"
        const val EXTRA_CALL_TYPE = "call_type"
        const val EXTRA_ROOM_ID = "room_id"
        const val CALL_NOTIFICATION_ID = 9999
    }
}

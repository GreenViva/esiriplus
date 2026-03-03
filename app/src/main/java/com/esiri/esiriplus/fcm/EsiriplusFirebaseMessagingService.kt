package com.esiri.esiriplus.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.esiri.esiriplus.EsiriplusApp
import com.esiri.esiriplus.MainActivity
import com.esiri.esiriplus.R
import com.esiri.esiriplus.call.IncomingCall
import com.esiri.esiriplus.call.IncomingCallStateHolder
import com.esiri.esiriplus.service.DoctorOnlineService
import com.esiri.esiriplus.service.overlay.OverlayBubbleManager
import com.esiri.esiriplus.core.database.dao.NotificationDao
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import com.esiri.esiriplus.core.domain.repository.NotificationRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val notificationId = remoteMessage.data["notification_id"]
        val type = remoteMessage.data["type"] ?: "GENERAL"

        // FCM fallback for consultation requests when realtime service is dead
        if (type.equals("CONSULTATION_REQUEST", ignoreCase = true) && DoctorOnlineService.isRunning) {
            // Service is running with active realtime — skip FCM notification to avoid duplicates
            Log.d(TAG, "Skipping CONSULTATION_REQUEST FCM — realtime service is active")
            return
        }

        if (type.equals("CONSULTATION_REQUEST", ignoreCase = true) && !DoctorOnlineService.isRunning) {
            // Realtime is dead — show high-priority notification as fallback
            Log.d(TAG, "CONSULTATION_REQUEST FCM fallback — service not running")
            val requestId = remoteMessage.data["request_id"] ?: notificationId ?: ""
            showConsultationRequestFallback(requestId)
            return
        }

        // Incoming video/voice call — show full-screen call notification
        if (type.equals("VIDEO_CALL_INCOMING", ignoreCase = true)) {
            val consultationId = remoteMessage.data["consultation_id"] ?: ""
            val roomId = remoteMessage.data["room_id"] ?: ""
            val callType = remoteMessage.data["call_type"] ?: "VIDEO"
            val callerRole = remoteMessage.data["caller_role"] ?: "doctor"
            Log.d(TAG, "Incoming call: consultation=$consultationId room=$roomId type=$callType")

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
        // Show a generic notification immediately so user sees something
        showNotification(
            title = getGenericTitle(type),
            body = "Tap to view details",
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
        Log.d(TAG, "New FCM token received")
        // Token will be pushed to Supabase by ViewModel on next app launch
    }

    private fun showConsultationRequestFallback(requestId: String) {
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
            .setContentTitle("New Consultation Request")
            .setContentText("A patient is waiting for you")
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
        val callLabel = if (callType == "AUDIO") "Voice" else "Video"
        val callerLabel = if (callerRole == "doctor") "Your doctor" else "Your patient"

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

        val notification = NotificationCompat.Builder(this, EsiriplusApp.CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming $callLabel Call")
            .setContentText("$callerLabel is calling")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(60_000)
            .setFullScreenIntent(acceptPending, true)
            .addAction(0, "Accept", acceptPending)
            .addAction(0, "Decline", declinePending)
            .build()

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

    private fun getGenericTitle(type: String): String = when (type.uppercase()) {
        "CONSULTATION_REQUEST" -> "New Consultation Request"
        "CONSULTATION_ACCEPTED" -> "Consultation Accepted"
        "MESSAGE_RECEIVED" -> "New Message"
        "VIDEO_CALL_INCOMING" -> "Incoming Video Call"
        "REPORT_READY" -> "Report Ready"
        "PAYMENT_STATUS" -> "Payment Update"
        "DOCTOR_APPROVED" -> "Application Update"
        "DOCTOR_REJECTED" -> "Application Update"
        "DOCTOR_WARNED" -> "Warning from Administration"
        "DOCTOR_SUSPENDED" -> "Account Suspended"
        "DOCTOR_UNSUSPENDED" -> "Account Reinstated"
        "DOCTOR_BANNED" -> "Account Banned"
        "DOCTOR_UNBANNED" -> "Account Reinstated"
        else -> "eSIRI+ Notification"
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

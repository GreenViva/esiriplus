package com.esiri.esiriplus.fcm

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.esiri.esiriplus.EsiriplusApp
import com.esiri.esiriplus.MainActivity
import com.esiri.esiriplus.R
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val notificationId = remoteMessage.data["notification_id"]
        val type = remoteMessage.data["type"] ?: "GENERAL"

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
    }
}

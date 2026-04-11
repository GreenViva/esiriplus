package com.esiri.esiriplus.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.esiri.esiriplus.EsiriplusApp
import com.esiri.esiriplus.MainActivity
import com.esiri.esiriplus.R
import com.esiri.esiriplus.core.common.locale.LocaleHelper
import com.esiri.esiriplus.fcm.CallDeclineBroadcastReceiver
import com.esiri.esiriplus.fcm.EsiriplusFirebaseMessagingService

/**
 * Short-lived foreground service that keeps the process alive while an incoming
 * call is ringing. Started from FCM [onMessageReceived] and auto-stops after
 * 60 seconds (matching the caller's waiting timeout).
 *
 * On aggressive OEMs (Samsung, OnePlus, Xiaomi) the OS may kill the app within
 * milliseconds of a data-only FCM delivery if no foreground service is running.
 * This service ensures the notification stays visible and the ringing completes.
 */
class IncomingCallService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val consultationId = intent?.getStringExtra(EXTRA_CONSULTATION_ID) ?: ""
        val callType = intent?.getStringExtra(EXTRA_CALL_TYPE) ?: "VIDEO"
        val callerRole = intent?.getStringExtra(EXTRA_CALLER_ROLE) ?: "doctor"
        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID) ?: ""

        val notification = buildCallNotification(consultationId, callType, callerRole, roomId)

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start foreground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        // Acquire a partial wake lock so the CPU stays on for the ringtone
        acquireWakeLock()

        // Auto-stop after 60s — call either accepted, declined, or timed out
        android.os.Handler(mainLooper).postDelayed({ stopSelf() }, TIMEOUT_MS)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        // FULL_WAKE_LOCK turns the screen on; ACQUIRE_CAUSES_WAKEUP wakes from sleep.
        // FULL_WAKE_LOCK is deprecated but is the only reliable way to light up the
        // screen from a Service on all OEMs including Samsung/OnePlus.
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                or PowerManager.ON_AFTER_RELEASE,
            "esiriplus:incoming_call",
        ).apply {
            acquire(TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun buildCallNotification(
        consultationId: String,
        callType: String,
        callerRole: String,
        roomId: String,
    ): Notification {
        val ctx = LocaleHelper.getLocalizedContext(this)
        val callLabel = if (callType == "AUDIO") ctx.getString(R.string.call_type_voice) else ctx.getString(R.string.call_type_video)
        val callerLabel = if (callerRole == "doctor") ctx.getString(R.string.caller_your_doctor) else ctx.getString(R.string.caller_your_patient)

        // Full-screen intent → just opens the app so the in-app overlay shows
        // Accept/Decline buttons. Does NOT auto-accept the call.
        val showAppIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SHOW_INCOMING_CALL
            putExtra(EsiriplusFirebaseMessagingService.EXTRA_CONSULTATION_ID, consultationId)
            putExtra(EsiriplusFirebaseMessagingService.EXTRA_CALL_TYPE, callType)
            putExtra(EsiriplusFirebaseMessagingService.EXTRA_ROOM_ID, roomId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val showAppPending = PendingIntent.getActivity(
            this, NOTIFICATION_ID + 2, showAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Accept action button → directly accepts the call
        val acceptIntent = Intent(this, MainActivity::class.java).apply {
            action = EsiriplusFirebaseMessagingService.ACTION_ACCEPT_CALL
            putExtra(EsiriplusFirebaseMessagingService.EXTRA_CONSULTATION_ID, consultationId)
            putExtra(EsiriplusFirebaseMessagingService.EXTRA_CALL_TYPE, callType)
            putExtra(EsiriplusFirebaseMessagingService.EXTRA_ROOM_ID, roomId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val acceptPending = PendingIntent.getActivity(
            this, NOTIFICATION_ID, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Decline action button → broadcast
        val declineIntent = Intent(this, CallDeclineBroadcastReceiver::class.java).apply {
            action = EsiriplusFirebaseMessagingService.ACTION_DECLINE_CALL
        }
        val declinePending = PendingIntent.getBroadcast(
            this, NOTIFICATION_ID + 1, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        return NotificationCompat.Builder(this, EsiriplusApp.CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_stethoscope_notif)
            .setContentTitle(ctx.getString(R.string.call_incoming_title, callLabel))
            .setContentText(ctx.getString(R.string.call_incoming_body, callerLabel))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(TIMEOUT_MS)
            .setFullScreenIntent(showAppPending, true)
            .setContentIntent(showAppPending)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, ctx.getString(R.string.action_accept), acceptPending)
            .addAction(0, ctx.getString(R.string.action_decline), declinePending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "IncomingCallService"
        const val NOTIFICATION_ID = 9998
        private const val TIMEOUT_MS = 60_000L
        const val ACTION_SHOW_INCOMING_CALL = "com.esiri.esiriplus.SHOW_INCOMING_CALL"

        const val EXTRA_CONSULTATION_ID = "consultation_id"
        const val EXTRA_CALL_TYPE = "call_type"
        const val EXTRA_CALLER_ROLE = "caller_role"
        const val EXTRA_ROOM_ID = "room_id"

        fun start(
            context: Context,
            consultationId: String,
            callType: String,
            callerRole: String,
            roomId: String,
        ) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                putExtra(EXTRA_CONSULTATION_ID, consultationId)
                putExtra(EXTRA_CALL_TYPE, callType)
                putExtra(EXTRA_CALLER_ROLE, callerRole)
                putExtra(EXTRA_ROOM_ID, roomId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IncomingCallService::class.java))
        }
    }
}

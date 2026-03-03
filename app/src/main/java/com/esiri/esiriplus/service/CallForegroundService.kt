package com.esiri.esiriplus.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.esiri.esiriplus.EsiriplusApp
import com.esiri.esiriplus.MainActivity
import com.esiri.esiriplus.R
import com.esiri.esiriplus.call.CallForegroundServiceStateHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CallForegroundService : Service() {

    @Inject lateinit var stateHolder: CallForegroundServiceStateHolder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Received ACTION_STOP")
                stateHolder.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val consultationId = intent.getStringExtra(EXTRA_CONSULTATION_ID) ?: ""
                val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "VIDEO"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, true)
                Log.d(TAG, "Starting call service: consultation=$consultationId type=$callType")
                startForeground(NOTIFICATION_ID, buildNotification(callType, 0))
                observeState()
            }
            else -> {
                // System restart — check if we have active state
                val state = stateHolder.state.value
                if (state != null && state.isActive) {
                    Log.d(TAG, "Recovering call service")
                    startForeground(NOTIFICATION_ID, buildNotification(state.callType, state.durationSeconds))
                    observeState()
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun observeState() {
        serviceScope.launch {
            stateHolder.state.collect { state ->
                if (state == null || !state.isActive) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                // Update notification with current duration
                val notification = buildNotification(state.callType, state.durationSeconds)
                try {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.notify(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update notification", e)
                }
            }
        }
    }

    private fun buildNotification(callType: String, durationSeconds: Int): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val endIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingEnd = PendingIntent.getService(
            this, 1, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val typeLabel = if (callType.equals("AUDIO", ignoreCase = true)) "Voice" else "Video"
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        val durationText = String.format("%02d:%02d", minutes, seconds)

        return NotificationCompat.Builder(this, CHANNEL_CALL_SERVICE)
            .setSmallIcon(R.drawable.ic_stethoscope)
            .setContentTitle("$typeLabel Call in Progress")
            .setContentText("Duration: $durationText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingTap)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End Call",
                pendingEnd,
            )
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "Call service destroyed")
    }

    companion object {
        private const val TAG = "CallForegroundService"
        const val ACTION_START = "com.esiri.esiriplus.call.START"
        const val ACTION_STOP = "com.esiri.esiriplus.call.STOP"
        const val EXTRA_CONSULTATION_ID = "consultation_id"
        const val EXTRA_CALL_TYPE = "call_type"
        const val EXTRA_IS_VIDEO = "is_video"
        const val CHANNEL_CALL_SERVICE = "call_service"
        private const val NOTIFICATION_ID = 9002
    }
}

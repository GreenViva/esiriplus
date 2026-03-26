package com.esiri.esiriplus.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.esiri.esiriplus.MainActivity
import com.esiri.esiriplus.R
import com.esiri.esiriplus.core.common.locale.LocaleHelper
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.interceptor.TokenRefresher
import com.esiri.esiriplus.core.network.service.ConsultationRequestRealtimeService
import com.esiri.esiriplus.service.overlay.OverlayBubbleManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/**
 * Foreground service that keeps the doctor's realtime subscription alive
 * when the app is backgrounded. Shows a persistent "You are online" notification
 * and an optional floating overlay bubble.
 */
@AndroidEntryPoint
class DoctorOnlineService : Service() {

    @Inject lateinit var realtimeService: ConsultationRequestRealtimeService
    @Inject lateinit var supabaseClientProvider: SupabaseClientProvider
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var tokenRefresher: TokenRefresher
    @Inject lateinit var overlayBubbleManager: OverlayBubbleManager
    @Inject lateinit var stateManager: DoctorOnlineStateManager
    @Inject lateinit var edgeFunctionClient: EdgeFunctionClient
    @Inject lateinit var userPreferencesManager: com.esiri.esiriplus.core.common.preferences.UserPreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tokenRefreshJob: Job? = null
    private var realtimeJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentDoctorId: String? = null
    private var reconnectAttempt = 0
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Received ACTION_STOP")
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RETRY_BUBBLE -> {
                Log.d(TAG, "Received ACTION_RETRY_BUBBLE")
                overlayBubbleManager.retryShowIfPermitted()
                return START_STICKY
            }
            ACTION_START -> {
                val doctorId = intent.getStringExtra(EXTRA_DOCTOR_ID)
                if (doctorId != null) {
                    Log.d(TAG, "Received ACTION_START for doctor=$doctorId")
                    stateManager.isOnline = true
                    stateManager.doctorId = doctorId
                    startForegroundWithNotification()
                    subscribeRealtime(doctorId)
                    startTokenRefresh()
                    overlayBubbleManager.show()
                    isRunning = true
                    logOnlineStatus("online")
                } else {
                    Log.e(TAG, "ACTION_START without doctorId")
                    stopSelf()
                }
            }
            else -> {
                // Null intent = restarted by system after process death (START_STICKY)
                val doctorId = stateManager.doctorId
                if (stateManager.isOnline && doctorId != null) {
                    Log.d(TAG, "Recovering after process death for doctor=$doctorId")
                    startForegroundWithNotification()
                    subscribeRealtime(doctorId)
                    startTokenRefresh()
                    overlayBubbleManager.show()
                    isRunning = true
                    logOnlineStatus("online")
                } else {
                    Log.d(TAG, "No persisted state, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildOnlineNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildOnlineNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val ctx = LocaleHelper.getLocalizedContext(this)
        return NotificationCompat.Builder(this, CHANNEL_DOCTOR_ONLINE)
            .setSmallIcon(R.drawable.ic_doctor_online)
            .setContentTitle(ctx.getString(R.string.doctor_online_title))
            .setContentText(ctx.getString(R.string.doctor_online_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun subscribeRealtime(doctorId: String) {
        currentDoctorId = doctorId
        reconnectAttempt = 0
        realtimeJob?.cancel()
        realtimeJob = serviceScope.launch {
            subscribeWithRetry(doctorId)
        }
        registerNetworkCallback()
    }

    private suspend fun subscribeWithRetry(doctorId: String) {
        while (reconnectAttempt < MAX_RECONNECT_RETRIES) {
            try {
                // Import auth token before subscribing so Supabase Realtime passes RLS checks.
                // Without this, the channel is created unauthenticated and INSERT events are
                // blocked by RLS policies that require auth.uid() = doctor_id.
                val access = tokenManager.getAccessTokenSync()
                val refresh = tokenManager.getRefreshTokenSync()
                if (access != null && refresh != null) {
                    try {
                        supabaseClientProvider.importAuthToken(access, refresh)
                    } catch (e: Exception) {
                        Log.w(TAG, "importAuthToken failed before realtime subscribe", e)
                    }
                }
                Log.d(TAG, "Subscribing realtime for doctor=$doctorId (attempt=$reconnectAttempt)")
                realtimeService.subscribeAsDoctor(doctorId, serviceScope)
                reconnectAttempt = 0 // reset on successful subscribe

                realtimeService.requestEvents.collect { event ->
                    when (event.status.uppercase()) {
                        "PENDING" -> {
                            Log.d(TAG, "Incoming request: ${event.requestId}")
                            overlayBubbleManager.showPulse(event.requestId)
                            startRinging()
                            startRepeatingVibration()
                            showIncomingRequestNotification(event.requestId)
                        }
                        "ACCEPTED", "REJECTED", "EXPIRED" -> {
                            Log.d(TAG, "Request ${event.requestId} resolved: ${event.status}")
                            stopRinging()
                            stopVibration()
                            overlayBubbleManager.resetPulse()
                            dismissIncomingRequestNotification(event.requestId)
                        }
                    }
                }
                // If collect completes normally (shouldn't for SharedFlow), reconnect
                Log.w(TAG, "Realtime collect completed unexpectedly, reconnecting...")
            } catch (e: Exception) {
                Log.e(TAG, "Realtime subscription error (attempt=$reconnectAttempt)", e)
            }

            // Exponential backoff: 2s, 4s, 8s, 16s, 30s max
            reconnectAttempt++
            val backoffMs = (INITIAL_BACKOFF_MS * (1L shl minOf(reconnectAttempt - 1, 4)))
                .coerceAtMost(MAX_BACKOFF_MS)
            Log.d(TAG, "Reconnecting in ${backoffMs}ms...")
            delay(backoffMs)
        }
        Log.e(TAG, "Max reconnect retries ($MAX_RECONNECT_RETRIES) exhausted, stopping service")
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerNetworkCallback() {
        unregisterNetworkCallback()
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available — triggering realtime reconnect")
                val doctorId = currentDoctorId ?: return
                // Cancel stale subscription and reconnect immediately
                realtimeJob?.cancel()
                reconnectAttempt = 0
                realtimeJob = serviceScope.launch {
                    // Small delay to let the network stabilize
                    delay(1500)
                    subscribeWithRetry(doctorId)
                }
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost")
            }
        }
        networkCallback = callback

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(it)
            } catch (_: Exception) { }
        }
        networkCallback = null
    }

    private fun startTokenRefresh() {
        tokenRefreshJob?.cancel()
        tokenRefreshJob = serviceScope.launch {
            while (true) {
                delay(TOKEN_REFRESH_INTERVAL_MS)
                try {
                    if (tokenManager.isTokenExpiringSoon(thresholdMinutes = 10)) {
                        val refreshToken = tokenManager.getRefreshTokenSync()
                        if (refreshToken != null) {
                            val success = tokenRefresher.refreshToken(refreshToken)
                            Log.d(TAG, "Periodic token refresh: success=$success")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Token refresh error", e)
                }
            }
        }
    }

    private fun showIncomingRequestNotification(requestId: String) {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(OverlayBubbleManager.EXTRA_ACTION, OverlayBubbleManager.ACTION_INCOMING_REQUEST)
            putExtra(OverlayBubbleManager.EXTRA_REQUEST_ID, requestId)
        }
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            requestId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val ctx = LocaleHelper.getLocalizedContext(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_INCOMING_REQUEST)
            .setSmallIcon(R.drawable.ic_stethoscope_notif)
            .setContentTitle(ctx.getString(R.string.notification_new_consultation))
            .setContentText(ctx.getString(R.string.notification_patient_waiting))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(60_000)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(
                INCOMING_NOTIFICATION_ID_BASE + requestId.hashCode(),
                notification,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted", e)
        }
    }

    private fun dismissIncomingRequestNotification(requestId: String) {
        try {
            NotificationManagerCompat.from(this).cancel(
                INCOMING_NOTIFICATION_ID_BASE + requestId.hashCode(),
            )
        } catch (_: Exception) { }
    }

    private fun startRinging() {
        try {
            stopRinging() // stop any previous ringtone
            val customUri = userPreferencesManager.requestRingtoneUri.value
            val uri = customUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
            Log.d(TAG, "Ringtone started")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start ringtone", e)
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.let {
                if (it.isPlaying) it.stop()
            }
            ringtone = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop ringtone", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun startRepeatingVibration() {
        try {
            stopVibration()
            // Pattern: wait 0ms, vibrate 800ms, pause 400ms — repeat from index 0
            val pattern = longArrayOf(0, 800, 400)
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = v
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                v.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop vibration", e)
        }
    }

    private fun cleanup() {
        Log.d(TAG, "Cleaning up")
        logOnlineStatus("offline")
        isRunning = false
        currentDoctorId = null
        stopRinging()
        stopVibration()
        tokenRefreshJob?.cancel()
        realtimeJob?.cancel()
        unregisterNetworkCallback()
        overlayBubbleManager.hide()
        serviceScope.launch {
            try {
                realtimeService.unsubscribe()
            } catch (e: Exception) {
                Log.e(TAG, "Error unsubscribing", e)
            }
        }
        stateManager.isOnline = false
    }

    override fun onDestroy() {
        cleanup()
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    private fun logOnlineStatus(action: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val body = buildJsonObject { put("action", JsonPrimitive(action)) }
                edgeFunctionClient.invoke("log-doctor-online", body)
                Log.d(TAG, "Logged doctor $action")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to log doctor $action", e)
            }
        }
    }

    companion object {
        private const val TAG = "DoctorOnlineService"
        const val ACTION_START = "com.esiri.esiriplus.service.START"
        const val ACTION_STOP = "com.esiri.esiriplus.service.STOP"
        const val ACTION_RETRY_BUBBLE = "com.esiri.esiriplus.service.RETRY_BUBBLE"
        const val EXTRA_DOCTOR_ID = "doctor_id"
        const val CHANNEL_DOCTOR_ONLINE = "doctor_online"
        const val CHANNEL_INCOMING_REQUEST = "doctor_incoming_request"
        private const val NOTIFICATION_ID = 9001
        private const val INCOMING_NOTIFICATION_ID_BASE = 9100
        private const val TOKEN_REFRESH_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes
        private const val INITIAL_BACKOFF_MS = 2000L  // 2 seconds
        private const val MAX_BACKOFF_MS = 30000L     // 30 seconds max
        private const val MAX_RECONNECT_RETRIES = 20  // stop after 20 consecutive failures

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, doctorId: String) {
            val intent = Intent(context, DoctorOnlineService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DOCTOR_ID, doctorId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DoctorOnlineService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun retryBubble(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, DoctorOnlineService::class.java).apply {
                action = ACTION_RETRY_BUBBLE
            }
            context.startService(intent)
        }
    }
}

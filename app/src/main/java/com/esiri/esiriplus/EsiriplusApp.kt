package com.esiri.esiriplus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.esiri.esiriplus.worker.NotificationCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import live.videosdk.rtc.android.VideoSDK
import javax.inject.Inject

@HiltAndroidApp
class EsiriplusApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        VideoSDK.initialize(applicationContext)
        createNotificationChannels()
        NotificationCleanupWorker.enqueue(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val mainChannel = NotificationChannel(
                CHANNEL_ID,
                "eSIRI+ Notifications",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "General notifications from eSIRI+"
            }
            notificationManager.createNotificationChannel(mainChannel)

            val onlineChannel = NotificationChannel(
                CHANNEL_DOCTOR_ONLINE,
                "Doctor Online Status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent notification when doctor is available for consultations"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(onlineChannel)

            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_REQUEST,
                "Incoming Consultation Requests",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts when a patient sends a consultation request"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(incomingChannel)

            val callChannel = NotificationChannel(
                CHANNEL_INCOMING_CALL,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts when you receive an incoming video or voice call"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)

            val callServiceChannel = NotificationChannel(
                CHANNEL_CALL_SERVICE,
                "Active Call",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent notification while a call is in progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(callServiceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "esiri_main"
        const val CHANNEL_DOCTOR_ONLINE = "doctor_online"
        const val CHANNEL_INCOMING_REQUEST = "doctor_incoming_request"
        const val CHANNEL_INCOMING_CALL = "esiri_incoming_call"
        const val CHANNEL_CALL_SERVICE = "call_service"
    }
}

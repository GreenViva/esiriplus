package com.esiri.esiriplus

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.esiri.esiriplus.core.common.locale.LocaleHelper
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
            val ctx = LocaleHelper.getLocalizedContext(this)

            val mainChannel = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.channel_main_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = ctx.getString(R.string.channel_main_description)
            }
            notificationManager.createNotificationChannel(mainChannel)

            val onlineChannel = NotificationChannel(
                CHANNEL_DOCTOR_ONLINE,
                ctx.getString(R.string.channel_doctor_online_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = ctx.getString(R.string.channel_doctor_online_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(onlineChannel)

            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_REQUEST,
                ctx.getString(R.string.channel_incoming_request_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = ctx.getString(R.string.channel_incoming_request_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(incomingChannel)

            val callChannel = NotificationChannel(
                CHANNEL_INCOMING_CALL,
                ctx.getString(R.string.channel_incoming_call_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = ctx.getString(R.string.channel_incoming_call_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)

            val callServiceChannel = NotificationChannel(
                CHANNEL_CALL_SERVICE,
                ctx.getString(R.string.channel_call_service_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = ctx.getString(R.string.channel_call_service_description)
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

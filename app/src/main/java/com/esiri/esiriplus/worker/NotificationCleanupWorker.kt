package com.esiri.esiriplus.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.esiri.esiriplus.core.domain.repository.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that deletes notifications older than 30 days.
 *
 * Medical privacy: ensures stale notification data doesn't persist
 * on-device longer than necessary. Runs once every 24 hours.
 */
@HiltWorker
class NotificationCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationRepository: NotificationRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val thirtyDaysAgo = System.currentTimeMillis() - RETENTION_PERIOD_MS
            notificationRepository.deleteOldNotifications(thirtyDaysAgo)
            Log.d(TAG, "Cleaned up notifications older than 30 days")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up old notifications", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NotifCleanupWorker"
        private const val WORK_NAME = "notification_cleanup"
        private const val RETENTION_PERIOD_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationCleanupWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

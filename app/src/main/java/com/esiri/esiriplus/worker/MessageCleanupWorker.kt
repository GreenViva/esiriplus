package com.esiri.esiriplus.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.esiri.esiriplus.core.database.dao.MessageDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that drops chat messages older than 14 days from the
 * local Room cache. Mirrors the server-side TTL so the patient's history
 * tab never shows stale entries. Runs once every 24 hours via WorkManager.
 */
@HiltWorker
class MessageCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageDao: MessageDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val cutoff = System.currentTimeMillis() - RETENTION_PERIOD_MS
            val deleted = messageDao.deleteOlderThan(cutoff)
            Log.d(TAG, "Pruned $deleted messages older than 14 days")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune old messages", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MsgCleanupWorker"
        private const val WORK_NAME = "message_cleanup"
        private const val RETENTION_PERIOD_MS = 14L * 24 * 60 * 60 * 1000 // 14 days

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<MessageCleanupWorker>(
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

package com.esiri.esiriplus.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.esiri.esiriplus.core.database.dao.MessageDao
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.MessageService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Persistent background worker that syncs undelivered messages.
 * Replaces in-memory MessageQueue retry for reliability across process death.
 * Only runs when network is available (WorkManager constraint).
 */
@HiltWorker
class MessageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageDao: MessageDao,
    private val messageService: MessageService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val unsynced = messageDao.getRetryableMessages(MAX_RETRIES)
        if (unsynced.isEmpty()) {
            Log.d(TAG, "No unsynced messages to process")
            return Result.success()
        }

        Log.d(TAG, "Processing ${unsynced.size} unsynced messages")
        var failedCount = 0

        for (msg in unsynced) {
            val backoffMs = backoffFor(msg.retryCount)
            val lastFailure = msg.failedAt
            if (lastFailure != null) {
                val elapsed = System.currentTimeMillis() - lastFailure
                if (elapsed < backoffMs) continue
            }

            when (messageService.sendMessage(
                messageId = msg.messageId,
                consultationId = msg.consultationId,
                senderType = msg.senderType,
                senderId = msg.senderId,
                messageText = msg.messageText,
                messageType = msg.messageType,
                attachmentUrl = msg.attachmentUrl,
            )) {
                is ApiResult.Success -> {
                    messageDao.markAsSynced(msg.messageId)
                    Log.d(TAG, "Synced message ${msg.messageId}")
                }
                else -> {
                    messageDao.incrementRetryCount(msg.messageId, System.currentTimeMillis())
                    failedCount++
                    Log.w(TAG, "Failed to sync message ${msg.messageId} (attempt ${msg.retryCount + 1})")
                }
            }
            delay(200)
        }

        return if (failedCount > 0) Result.retry() else Result.success()
    }

    private fun backoffFor(retryCount: Int): Long = when (retryCount) {
        0 -> 1_000L
        1 -> 5_000L
        2 -> 30_000L
        3 -> 5 * 60_000L
        else -> 15 * 60_000L
    }

    companion object {
        private const val TAG = "MessageSyncWorker"
        private const val WORK_NAME = "message_sync"
        private const val MAX_RETRIES = 5

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<MessageSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

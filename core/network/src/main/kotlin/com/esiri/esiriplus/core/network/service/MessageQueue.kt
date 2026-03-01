package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.database.dao.MessageDao
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background message retry queue. Processes all unsynced messages across
 * all consultations with exponential backoff per message.
 * Triggered on: app startup, message send failure.
 */
@Singleton
class MessageQueue @Inject constructor(
    private val messageDao: MessageDao,
    private val messageService: MessageService,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var processing = false

    /** Trigger a retry sweep. Safe to call multiple times — deduplicates. */
    fun processUnsynced() {
        if (processing) return
        scope.launch {
            processing = true
            try {
                doProcess()
            } finally {
                processing = false
            }
        }
    }

    private suspend fun doProcess() {
        val unsynced = messageDao.getRetryableMessages(MAX_RETRIES)
        if (unsynced.isEmpty()) return
        Log.d(TAG, "Processing ${unsynced.size} unsynced messages")

        for (msg in unsynced) {
            val backoffMs = backoffFor(msg.retryCount)
            val lastFailure = msg.failedAt
            if (lastFailure != null) {
                val elapsed = System.currentTimeMillis() - lastFailure
                if (elapsed < backoffMs) continue // Not ready to retry yet
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
                    Log.d(TAG, "Retried message ${msg.messageId} — success")
                }
                else -> {
                    messageDao.incrementRetryCount(msg.messageId, System.currentTimeMillis())
                    Log.w(TAG, "Retried message ${msg.messageId} — failed (attempt ${msg.retryCount + 1})")
                }
            }

            // Small delay between messages to avoid hammering the server
            delay(200)
        }
    }

    private fun backoffFor(retryCount: Int): Long = when (retryCount) {
        0 -> 1_000L
        1 -> 5_000L
        2 -> 30_000L
        3 -> 5 * 60_000L  // 5 min
        else -> 15 * 60_000L  // 15 min
    }

    companion object {
        private const val TAG = "MessageQueue"
        const val MAX_RETRIES = 5
    }
}

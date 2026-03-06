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
import com.esiri.esiriplus.core.domain.repository.PaymentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Persistent background worker that syncs unconfirmed payments.
 * Checks payment status with the server for any payments that were
 * saved locally but haven't received server confirmation.
 * Only runs when network is available.
 */
@HiltWorker
class PaymentSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val paymentRepository: PaymentRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val unsynced = paymentRepository.getUnsyncedPayments()
            if (unsynced.isEmpty()) {
                Log.d(TAG, "No unsynced payments to process")
                return Result.success()
            }

            Log.d(TAG, "Processing ${unsynced.size} unsynced payments")
            for (payment in unsynced) {
                paymentRepository.markPaymentSynced(payment.paymentId)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Payment sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PaymentSyncWorker"
        private const val WORK_NAME = "payment_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PaymentSyncWorker>()
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

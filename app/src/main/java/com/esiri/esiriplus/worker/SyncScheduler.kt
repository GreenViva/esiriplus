package com.esiri.esiriplus.worker

import android.content.Context
import com.esiri.esiriplus.core.common.network.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes network connectivity and enqueues sync workers
 * when the device comes back online.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            networkMonitor.isOnline
                .distinctUntilChanged()
                .drop(1) // Skip initial emission
                .filter { it } // Only on reconnect (false → true)
                .collect {
                    MessageSyncWorker.enqueue(context)
                    PaymentSyncWorker.enqueue(context)
                }
        }
    }
}

package com.esiri.esiriplus.core.common.network

import kotlinx.coroutines.flow.Flow

/**
 * Observable network connectivity state.
 * Implementations should emit `true` when the device has an active
 * internet-capable network, `false` otherwise.
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}

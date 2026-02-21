package com.esiri.esiriplus.core.network

/**
 * Interface to break the circular dependency between network layer and auth feature.
 * Implemented by the auth feature module, injected into network interceptors.
 * Called when token refresh fails to clear all session state.
 */
interface SessionInvalidator {
    fun invalidate()
}

package com.esiri.esiriplus.core.domain.repository

interface FcmTokenRepository {
    /**
     * Upsert the token into `fcm_tokens`. Returns `true` on success (HTTP 2xx),
     * `false` if the server rejected or the network failed.
     */
    suspend fun registerToken(token: String, userId: String): Boolean
    suspend fun getStoredToken(): String?
    suspend fun clearToken()

    /**
     * Fetch a fresh token from Firebase and register it with the server,
     * retrying on transient failures.
     *
     * Returns `true` if the token is now confirmed in `fcm_tokens`, `false`
     * if every attempt failed. Callers (e.g. the doctor dashboard) should
     * surface `false` as a "Notifications unavailable" banner — the doctor
     * will silently miss every consultation request until this succeeds.
     */
    suspend fun fetchAndRegisterToken(userId: String, maxAttempts: Int = 3): Boolean
}

package com.esiri.esiriplus.core.domain.repository

interface FcmTokenRepository {
    suspend fun registerToken(token: String, userId: String)
    suspend fun getStoredToken(): String?
    suspend fun clearToken()
    suspend fun fetchAndRegisterToken(userId: String)
}

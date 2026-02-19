package com.esiri.esiriplus.core.network

import com.esiri.esiriplus.core.network.security.EncryptedTokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val encryptedTokenStorage: EncryptedTokenStorage,
) {
    private val _accessToken = MutableStateFlow(encryptedTokenStorage.getAccessToken())
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow(encryptedTokenStorage.getRefreshToken())
    val refreshToken: StateFlow<String?> = _refreshToken.asStateFlow()

    fun getAccessTokenSync(): String? = encryptedTokenStorage.getAccessToken()

    fun getRefreshTokenSync(): String? = encryptedTokenStorage.getRefreshToken()

    fun isTokenExpiringSoon(thresholdMinutes: Int = 5): Boolean =
        encryptedTokenStorage.isTokenExpiringSoon(thresholdMinutes)

    fun saveTokens(accessToken: String, refreshToken: String, expiresAtMillis: Long) {
        encryptedTokenStorage.saveTokens(accessToken, refreshToken, expiresAtMillis)
        _accessToken.value = accessToken
        _refreshToken.value = refreshToken
    }

    fun clearTokens() {
        encryptedTokenStorage.clearTokens()
        _accessToken.value = null
        _refreshToken.value = null
    }
}

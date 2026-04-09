package com.esiri.esiriplus.core.network

import com.esiri.esiriplus.core.common.session.SessionBackup
import com.esiri.esiriplus.core.network.security.EncryptedTokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val encryptedTokenStorage: EncryptedTokenStorage,
    private val sessionBackup: SessionBackup,
) {
    // Initialize from the best available source. EncryptedSharedPreferences on Samsung
    // frequently loses data, so always check the plain backup as fallback.
    private val _accessToken: MutableStateFlow<String?>
    val accessToken: StateFlow<String?>

    private val _refreshToken: MutableStateFlow<String?>
    val refreshToken: StateFlow<String?>

    init {
        val encAccess = encryptedTokenStorage.getAccessToken()
        val encRefresh = encryptedTokenStorage.getRefreshToken()
        val backupAccess = sessionBackup.accessToken
        val backupRefresh = sessionBackup.refreshToken

        val initAccess = encAccess ?: backupAccess
        val initRefresh = encRefresh ?: backupRefresh

        _accessToken = MutableStateFlow(initAccess)
        accessToken = _accessToken.asStateFlow()
        _refreshToken = MutableStateFlow(initRefresh)
        refreshToken = _refreshToken.asStateFlow()

        // If encrypted storage lost tokens but backup has them, restore
        if (encAccess == null && backupAccess != null && backupRefresh != null) {
            android.util.Log.w("TokenManager", "Encrypted storage empty but backup has tokens — restoring")
            try {
                encryptedTokenStorage.saveTokens(backupAccess, backupRefresh, sessionBackup.expiresAtMillis)
            } catch (_: Exception) { /* best effort */ }
        }

        android.util.Log.d("TokenManager", "init: access=${initAccess?.take(20)}..., refresh=${if (initRefresh != null) "present" else "NULL"}")
    }

    /**
     * Called from AuthRepositoryImpl when currentSession emits — ensures the
     * in-memory cache has the token even if EncryptedSharedPreferences lost it.
     */
    fun restoreFromSession(accessToken: String, refreshToken: String, expiresAtMillis: Long) {
        if (_accessToken.value == null) {
            android.util.Log.w("TokenManager", "Restoring tokens from Room session")
            _accessToken.value = accessToken
            _refreshToken.value = refreshToken
            try {
                sessionBackup.saveTokensOnly(accessToken, refreshToken, expiresAtMillis)
                encryptedTokenStorage.saveTokens(accessToken, refreshToken, expiresAtMillis)
            } catch (_: Exception) { /* best effort */ }
        }
    }

    /**
     * Returns the access token. Prefers in-memory cache (always up-to-date),
     * falls back to encrypted storage, then plain backup.
     * EncryptedSharedPreferences on Samsung loses data — never rely on it alone.
     */
    fun getAccessTokenSync(): String? =
        _accessToken.value
            ?: encryptedTokenStorage.getAccessToken()
            ?: sessionBackup.accessToken

    fun getRefreshTokenSync(): String? =
        _refreshToken.value
            ?: encryptedTokenStorage.getRefreshToken()
            ?: sessionBackup.refreshToken

    fun getExpiresAtMillis(): Long {
        val encrypted = encryptedTokenStorage.getExpiresAt()
        return if (encrypted > 0L) encrypted else sessionBackup.expiresAtMillis
    }

    fun isTokenExpiringSoon(thresholdMinutes: Int = 5): Boolean {
        val expiresAt = getExpiresAtMillis()
        if (expiresAt == 0L) return true
        val thresholdMillis = thresholdMinutes * 60 * 1000L
        return System.currentTimeMillis() + thresholdMillis >= expiresAt
    }

    fun saveTokens(accessToken: String, refreshToken: String, expiresAtMillis: Long) {
        // In-memory cache (most reliable — survives EncryptedSharedPreferences failures)
        _accessToken.value = accessToken
        _refreshToken.value = refreshToken
        // Encrypted storage (may fail silently on Samsung)
        encryptedTokenStorage.saveTokens(accessToken, refreshToken, expiresAtMillis)
        // Plain backup (nuclear fallback — always works)
        try {
            sessionBackup.saveTokensOnly(accessToken, refreshToken, expiresAtMillis)
        } catch (_: Exception) { /* non-critical */ }
    }

    fun clearTokens() {
        encryptedTokenStorage.clearTokens()
        _accessToken.value = null
        _refreshToken.value = null
    }
}

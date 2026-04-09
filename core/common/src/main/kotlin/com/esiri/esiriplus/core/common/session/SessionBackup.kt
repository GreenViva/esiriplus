package com.esiri.esiriplus.core.common.session

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain SharedPreferences backup of essential session data.
 * This survives Android Keystore failures, EncryptedSharedPreferences corruption,
 * SQLCipher passphrase loss, and Room database wipes.
 *
 * Stores just enough to silently restore the session on cold start:
 *  - user ID, role, name (for immediate UI)
 *  - an obfuscated refresh token (for re-authenticating with Supabase)
 *
 * NOT a security boundary — the refresh token is Base64-encoded, not encrypted.
 * The Android app sandbox provides file-level protection. If the device is rooted,
 * the Supabase refresh token is already exposed via EncryptedSharedPreferences anyway
 * (Keystore keys are extractable on rooted devices).
 */
@Singleton
class SessionBackup @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val hasBackup: Boolean
        get() = prefs.contains(KEY_USER_ID)

    val userId: String?
        get() = prefs.getString(KEY_USER_ID, null)

    val userRole: String?
        get() = prefs.getString(KEY_USER_ROLE, null)

    val userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)

    val userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)

    val isVerified: Boolean
        get() = prefs.getBoolean(KEY_IS_VERIFIED, false)

    val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN_B64, null)?.let { decode(it) }

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN_B64, null)?.let { decode(it) }

    val expiresAtMillis: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun save(
        userId: String,
        role: String,
        fullName: String,
        email: String?,
        isVerified: Boolean,
        refreshToken: String,
        accessToken: String? = null,
        expiresAtMillis: Long = 0L,
    ) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_ROLE, role)
            .putString(KEY_USER_NAME, fullName)
            .putString(KEY_USER_EMAIL, email)
            .putBoolean(KEY_IS_VERIFIED, isVerified)
            .putString(KEY_REFRESH_TOKEN_B64, encode(refreshToken))
            .apply {
                if (accessToken != null) putString(KEY_ACCESS_TOKEN_B64, encode(accessToken))
                if (expiresAtMillis > 0) putLong(KEY_EXPIRES_AT, expiresAtMillis)
            }
            .commit()
    }

    /** Update only the tokens without touching user info. Called by TokenManager on every save. */
    fun saveTokensOnly(accessToken: String, refreshToken: String, expiresAtMillis: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN_B64, encode(accessToken))
            .putString(KEY_REFRESH_TOKEN_B64, encode(refreshToken))
            .putLong(KEY_EXPIRES_AT, expiresAtMillis)
            .commit()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)

    private fun decode(encoded: String): String =
        String(Base64.decode(encoded, Base64.NO_WRAP))

    companion object {
        private const val PREFS_NAME = "esiriplus_session_backup"
        private const val KEY_USER_ID = "uid"
        private const val KEY_USER_ROLE = "role"
        private const val KEY_USER_NAME = "name"
        private const val KEY_USER_EMAIL = "email"
        private const val KEY_IS_VERIFIED = "verified"
        private const val KEY_REFRESH_TOKEN_B64 = "rt"
        private const val KEY_ACCESS_TOKEN_B64 = "at"
        private const val KEY_EXPIRES_AT = "exp"
    }
}

package com.esiri.esiriplus.feature.auth.recovery

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecoveryRateLimiter @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.w("RecoveryRateLimiter", "Encrypted prefs corrupted, clearing and recreating", e)
        context.deleteSharedPreferences(PREFS_NAME)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun canAttempt(): Boolean {
        resetIfWindowExpired()
        return getAttemptCount() < MAX_ATTEMPTS
    }

    fun recordAttempt() {
        resetIfWindowExpired()
        val count = getAttemptCount()
        if (count == 0) {
            prefs.edit()
                .putLong(KEY_WINDOW_START, System.currentTimeMillis())
                .putInt(KEY_ATTEMPT_COUNT, 1)
                .apply()
        } else {
            prefs.edit()
                .putInt(KEY_ATTEMPT_COUNT, count + 1)
                .apply()
        }
    }

    fun remainingAttempts(): Int {
        resetIfWindowExpired()
        return (MAX_ATTEMPTS - getAttemptCount()).coerceAtLeast(0)
    }

    private fun getAttemptCount(): Int = prefs.getInt(KEY_ATTEMPT_COUNT, 0)

    private fun resetIfWindowExpired() {
        val windowStart = prefs.getLong(KEY_WINDOW_START, 0L)
        if (windowStart > 0 && System.currentTimeMillis() - windowStart > WINDOW_MILLIS) {
            prefs.edit()
                .remove(KEY_WINDOW_START)
                .remove(KEY_ATTEMPT_COUNT)
                .apply()
        }
    }

    companion object {
        const val PREFS_NAME = "esiriplus_recovery_rate_limit"
        private const val KEY_WINDOW_START = "window_start"
        private const val KEY_ATTEMPT_COUNT = "attempt_count"
        private const val MAX_ATTEMPTS = 5
        private const val WINDOW_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
    }
}

package com.esiri.esiriplus.feature.auth.biometric

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricPreferenceStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    companion object {
        const val PREFS_NAME = "esiriplus_biometric_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
}

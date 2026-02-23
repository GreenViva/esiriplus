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

    fun getBoundDoctorId(): String? = prefs.getString(KEY_BOUND_DOCTOR_ID, null)

    fun setBoundDoctorId(doctorId: String) {
        prefs.edit().putString(KEY_BOUND_DOCTOR_ID, doctorId).apply()
    }

    fun getDeviceFingerprint(): String? = prefs.getString(KEY_DEVICE_FINGERPRINT, null)

    fun setDeviceFingerprint(fingerprint: String) {
        prefs.edit().putString(KEY_DEVICE_FINGERPRINT, fingerprint).apply()
    }

    fun getBiometricEnrollmentTimestamp(): Long =
        prefs.getLong(KEY_BIOMETRIC_ENROLLMENT_TIMESTAMP, 0L)

    fun setBiometricEnrollmentTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_BIOMETRIC_ENROLLMENT_TIMESTAMP, timestamp).apply()
    }

    fun getPreferredBiometricType(): String? =
        prefs.getString(KEY_PREFERRED_BIOMETRIC_TYPE, null)

    fun setPreferredBiometricType(type: String) {
        prefs.edit().putString(KEY_PREFERRED_BIOMETRIC_TYPE, type).apply()
    }

    fun clearAllBindingData() {
        prefs.edit()
            .remove(KEY_BIOMETRIC_ENABLED)
            .remove(KEY_BOUND_DOCTOR_ID)
            .remove(KEY_DEVICE_FINGERPRINT)
            .remove(KEY_BIOMETRIC_ENROLLMENT_TIMESTAMP)
            .remove(KEY_PREFERRED_BIOMETRIC_TYPE)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "esiriplus_biometric_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BOUND_DOCTOR_ID = "bound_doctor_id"
        private const val KEY_DEVICE_FINGERPRINT = "device_fingerprint"
        private const val KEY_BIOMETRIC_ENROLLMENT_TIMESTAMP = "biometric_enrollment_timestamp"
        private const val KEY_PREFERRED_BIOMETRIC_TYPE = "preferred_biometric_type"
    }
}

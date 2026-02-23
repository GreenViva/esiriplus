package com.esiri.esiriplus.feature.auth.biometric

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceBindingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceStorage: BiometricPreferenceStorage,
) {
    @SuppressLint("HardwareIds")
    fun getDeviceFingerprint(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "unknown"
        val raw = "$androidId|${Build.MANUFACTURER}|${Build.MODEL}|${Build.BOARD}"
        return sha256(raw)
    }

    fun getBoundDoctorId(): String? = preferenceStorage.getBoundDoctorId()

    fun bindDevice(doctorId: String) {
        preferenceStorage.setBoundDoctorId(doctorId)
        preferenceStorage.setDeviceFingerprint(getDeviceFingerprint())
        preferenceStorage.setBiometricEnrollmentTimestamp(System.currentTimeMillis())
        preferenceStorage.setBiometricEnabled(true)
    }

    fun isDeviceBound(): Boolean = preferenceStorage.getBoundDoctorId() != null

    fun isDeviceBoundTo(doctorId: String): Boolean =
        preferenceStorage.getBoundDoctorId() == doctorId

    fun clearBinding() {
        preferenceStorage.clearAllBindingData()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

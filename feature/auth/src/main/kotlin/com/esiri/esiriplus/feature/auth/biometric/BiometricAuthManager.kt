package com.esiri.esiriplus.feature.auth.biometric

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class BiometricType {
    FINGERPRINT,
    FACE,
}

@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceStorage: BiometricPreferenceStorage,
) {
    private val biometricManager = BiometricManager.from(context)

    /** Device has biometric hardware (fingerprint, face, etc.). */
    fun hasHardware(): Boolean {
        val result = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
        return result != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
            result != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    }

    /** User has enrolled at least one biometric on the device. */
    fun hasEnrolledBiometrics(): Boolean {
        val result = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isAvailable(): Boolean = hasEnrolledBiometrics()

    fun isEnabled(): Boolean = preferenceStorage.isBiometricEnabled()

    fun setEnabled(enabled: Boolean) = preferenceStorage.setBiometricEnabled(enabled)

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onResult: (BiometricResult) -> Unit,
    ) {
        if (!hasEnrolledBiometrics()) {
            onResult(BiometricResult.NotAvailable)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onResult(BiometricResult.Cancelled)
                } else {
                    onResult(BiometricResult.Error(errString.toString()))
                }
            }

            override fun onAuthenticationFailed() {
                // Individual attempt failure; prompt stays open
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }

    fun hasFingerprint(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

    fun hasFaceAuth(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)

    fun getAvailableTypes(): List<BiometricType> = buildList {
        if (hasFingerprint()) add(BiometricType.FINGERPRINT)
        if (hasFaceAuth()) add(BiometricType.FACE)
    }

    fun setPreferredBiometricType(type: BiometricType) {
        preferenceStorage.setPreferredBiometricType(type.name)
    }

    fun getPreferredBiometricType(): BiometricType? {
        val name = preferenceStorage.getPreferredBiometricType() ?: return null
        return runCatching { BiometricType.valueOf(name) }.getOrNull()
    }

    sealed interface BiometricResult {
        data object Success : BiometricResult
        data object Cancelled : BiometricResult
        data class Error(val message: String) : BiometricResult
        data object NotAvailable : BiometricResult
    }
}

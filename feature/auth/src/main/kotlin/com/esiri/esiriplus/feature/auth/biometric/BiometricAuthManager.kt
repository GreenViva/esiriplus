package com.esiri.esiriplus.feature.auth.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceStorage: BiometricPreferenceStorage,
) {
    private val biometricManager = BiometricManager.from(context)

    fun isAvailable(): Boolean {
        val result = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isEnabled(): Boolean = preferenceStorage.isBiometricEnabled()

    fun setEnabled(enabled: Boolean) = preferenceStorage.setBiometricEnabled(enabled)

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onResult: (BiometricResult) -> Unit,
    ) {
        if (!isAvailable()) {
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
                // Called on individual attempt failure; prompt stays open, so no action needed
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }

    sealed interface BiometricResult {
        data object Success : BiometricResult
        data object Cancelled : BiometricResult
        data class Error(val message: String) : BiometricResult
        data object NotAvailable : BiometricResult
    }
}

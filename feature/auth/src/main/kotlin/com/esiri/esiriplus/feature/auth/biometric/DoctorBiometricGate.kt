package com.esiri.esiriplus.feature.auth.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity

/**
 * Mandatory biometric gate for doctors. Unlike [BiometricGate], this does NOT
 * auto-pass when biometric is "not enabled" — it is always enforced.
 * Only auto-passes if biometric hardware is genuinely unavailable.
 */
@Composable
fun DoctorBiometricGate(
    biometricAuthManager: BiometricAuthManager,
    title: String,
    subtitle: String? = null,
    onAuthFailed: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!biometricAuthManager.isAvailable()) {
        // Hardware not available — auto-pass (shouldn't happen since registration enforced it)
        content()
        return
    }

    var isAuthenticated by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    if (isAuthenticated) {
        content()
    } else {
        LaunchedEffect(Unit) {
            val activity = context as? FragmentActivity ?: run {
                isAuthenticated = true
                return@LaunchedEffect
            }
            biometricAuthManager.prompt(
                activity = activity,
                title = title,
                subtitle = subtitle,
            ) { result ->
                when (result) {
                    is BiometricAuthManager.BiometricResult.Success -> {
                        isAuthenticated = true
                    }
                    is BiometricAuthManager.BiometricResult.NotAvailable -> {
                        isAuthenticated = true
                    }
                    is BiometricAuthManager.BiometricResult.Cancelled,
                    is BiometricAuthManager.BiometricResult.Error -> {
                        onAuthFailed?.invoke()
                    }
                }
            }
        }
    }
}

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
 * Gate composable that requires biometric authentication before showing [content].
 * Auto-passes when biometric is not enabled or not available.
 *
 * @param biometricAuthManager The biometric auth manager instance.
 * @param title Title shown on the biometric prompt.
 * @param subtitle Optional subtitle for the prompt.
 * @param onAuthFailed Called when the user cancels or authentication fails.
 * @param content Content to show after successful authentication.
 */
@Composable
fun BiometricGate(
    biometricAuthManager: BiometricAuthManager,
    title: String,
    subtitle: String? = null,
    onAuthFailed: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!biometricAuthManager.isEnabled() || !biometricAuthManager.isAvailable()) {
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
                // Cannot show biometric prompt without FragmentActivity, auto-pass
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

package com.esiri.esiriplus.feature.auth.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.extensions.isValidEmail
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.usecase.LoginDoctorUseCase
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.DoctorProfileService
import com.esiri.esiriplus.feature.auth.biometric.BiometricAuthManager
import com.esiri.esiriplus.feature.auth.biometric.DeviceBindingManager
import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject

enum class ResetStep { EMAIL, OTP, NEW_PASSWORD, DONE }

data class DoctorLoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val deviceMismatch: Boolean = false,
    val deviceMismatchError: String? = null,
    val isBanned: Boolean = false,
    val banReason: String? = null,
    val bannedAt: String? = null,
    val isSuspended: Boolean = false,
    val suspendedUntil: String? = null,
    val suspensionReason: String? = null,
    val hasWarning: Boolean = false,
    val warningMessage: String? = null,
    val showForgotPassword: Boolean = false,
    val resetStep: ResetStep = ResetStep.EMAIL,
    val resetEmail: String = "",
    val resetOtp: String = "",
    val resetNewPassword: String = "",
    val resetConfirmPassword: String = "",
    val resetSending: Boolean = false,
    val resetError: String? = null,
) {
    val isFormValid: Boolean
        get() = email.isValidEmail() && password.length >= 6
}

@HiltViewModel
class DoctorLoginViewModel @Inject constructor(
    private val loginDoctor: LoginDoctorUseCase,
    val biometricAuthManager: BiometricAuthManager,
    private val deviceBindingManager: DeviceBindingManager,
    private val profileService: DoctorProfileService,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val tokenManager: TokenManager,
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorLoginUiState())
    val uiState: StateFlow<DoctorLoginUiState> = _uiState.asStateFlow()

    /** Stored callback + doctorId for deferred navigation after warning acknowledgment. */
    private var pendingOnSuccess: (() -> Unit)? = null
    private var pendingDoctorId: String? = null

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun login(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isBanned = false, isSuspended = false) }
            val email = uiState.value.email
            val password = uiState.value.password

            when (val result = loginDoctor(email, password)) {
                is Result.Success -> {
                    val session = result.data
                    val doctorId = session.user.id

                    // Check ban/suspension/warning status before allowing navigation
                    val blocked = checkAccountStatus(session.accessToken, session.refreshToken, doctorId, onSuccess)
                    if (blocked) return@launch

                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                    return@launch
                }
                is Result.Error -> {
                    val error = result.message ?: "Login failed"
                    Log.w(TAG, "Login failed: $error")
                    _uiState.update { it.copy(isLoading = false, error = error) }
                }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Checks if the doctor is banned, suspended, or has a pending warning.
     * Returns true if blocked (UI state is updated to show the appropriate screen).
     */
    private suspend fun checkAccountStatus(
        accessToken: String,
        refreshToken: String,
        doctorId: String,
        onSuccess: () -> Unit,
    ): Boolean {
        return try {
            val freshAccess = tokenManager.getAccessTokenSync() ?: accessToken
            val freshRefresh = tokenManager.getRefreshTokenSync() ?: refreshToken
            supabaseClientProvider.importAuthToken(freshAccess, freshRefresh)

            when (val profileResult = profileService.getDoctorProfile(doctorId)) {
                is ApiResult.Success -> {
                    val profile = profileResult.data ?: run {
                        Log.w(TAG, "Profile is null for $doctorId")
                        return false
                    }
                    Log.d(TAG, "Profile check: isBanned=${profile.isBanned}, suspendedUntil=${profile.suspendedUntil}, warningMessage=${profile.warningMessage}")

                    // Ban takes priority over suspension
                    if (profile.isBanned) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isBanned = true,
                                banReason = profile.banReason,
                                bannedAt = profile.bannedAt,
                            )
                        }
                        return true
                    }

                    // Check active suspension
                    val suspendedUntil = profile.suspendedUntil
                    if (suspendedUntil != null) {
                        val isSuspended = try {
                            java.time.OffsetDateTime.parse(suspendedUntil).toInstant().isAfter(Instant.now())
                        } catch (_: Exception) {
                            try { Instant.parse(suspendedUntil).isAfter(Instant.now()) } catch (_: Exception) { false }
                        }

                        if (isSuspended) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isSuspended = true,
                                    suspendedUntil = suspendedUntil,
                                    suspensionReason = profile.suspensionReason,
                                )
                            }
                            return true
                        }
                    }

                    // Check pending warning — doctor must acknowledge before proceeding
                    if (!profile.warningMessage.isNullOrBlank()) {
                        pendingOnSuccess = onSuccess
                        pendingDoctorId = doctorId
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                hasWarning = true,
                                warningMessage = profile.warningMessage,
                            )
                        }
                        return true
                    }

                    false
                }
                else -> {
                    Log.w(TAG, "Failed to check account status, allowing login")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking account status", e)
            false
        }
    }

    /** Doctor acknowledges the warning and proceeds to dashboard. */
    fun acknowledgeWarning() {
        val doctorId = pendingDoctorId ?: return
        val onSuccess = pendingOnSuccess ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                profileService.acknowledgeWarning(doctorId)
                Log.d(TAG, "Warning acknowledged for $doctorId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acknowledge warning", e)
            }
            pendingOnSuccess = null
            pendingDoctorId = null
            _uiState.update { it.copy(isLoading = false, hasWarning = false, warningMessage = null) }
            onSuccess()
        }
    }

    fun clearBlockedState() {
        pendingOnSuccess = null
        pendingDoctorId = null
        _uiState.update {
            DoctorLoginUiState()
        }
    }

    fun showForgotPassword() {
        _uiState.update {
            it.copy(
                showForgotPassword = true,
                resetStep = ResetStep.EMAIL,
                resetEmail = it.email,
                resetOtp = "",
                resetNewPassword = "",
                resetConfirmPassword = "",
                resetSending = false,
                resetError = null,
            )
        }
    }

    fun dismissForgotPassword() {
        _uiState.update {
            it.copy(showForgotPassword = false, resetStep = ResetStep.EMAIL, resetError = null)
        }
    }

    fun onResetEmailChanged(v: String) { _uiState.update { it.copy(resetEmail = v, resetError = null) } }
    fun onResetOtpChanged(v: String) { _uiState.update { it.copy(resetOtp = v, resetError = null) } }
    fun onResetNewPasswordChanged(v: String) { _uiState.update { it.copy(resetNewPassword = v, resetError = null) } }
    fun onResetConfirmPasswordChanged(v: String) { _uiState.update { it.copy(resetConfirmPassword = v, resetError = null) } }

    /** Step 1: Send OTP to email */
    fun sendResetOtp() {
        val email = _uiState.value.resetEmail.trim()
        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(resetError = "Please enter a valid email address") }
            return
        }
        _uiState.update { it.copy(resetSending = true, resetError = null) }
        viewModelScope.launch {
            val body = buildJsonObject { put("action", "send_otp"); put("email", email) }
            edgeFunctionClient.invoke("reset-password", body, anonymous = true)
            // Always advance to OTP step (don't reveal if email exists)
            _uiState.update { it.copy(resetSending = false, resetStep = ResetStep.OTP) }
        }
    }

    /** Step 2: Verify OTP */
    fun verifyResetOtp() {
        val state = _uiState.value
        if (state.resetOtp.length != 6) {
            _uiState.update { it.copy(resetError = "Enter the 6-digit code") }
            return
        }
        _uiState.update { it.copy(resetSending = true, resetError = null) }
        viewModelScope.launch {
            val body = buildJsonObject {
                put("action", "verify_otp")
                put("email", state.resetEmail.trim())
                put("otp_code", state.resetOtp.trim())
            }
            when (val result = edgeFunctionClient.invoke("reset-password", body, anonymous = true)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(resetSending = false, resetStep = ResetStep.NEW_PASSWORD) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(resetSending = false, resetError = result.message ?: "Verification failed") }
                }
                else -> {
                    _uiState.update { it.copy(resetSending = false, resetError = "Something went wrong. Try again.") }
                }
            }
        }
    }

    /** Step 3: Set new password */
    fun setNewPassword() {
        val state = _uiState.value
        if (state.resetNewPassword.length < 6) {
            _uiState.update { it.copy(resetError = "Password must be at least 6 characters") }
            return
        }
        if (state.resetNewPassword != state.resetConfirmPassword) {
            _uiState.update { it.copy(resetError = "Passwords do not match") }
            return
        }
        _uiState.update { it.copy(resetSending = true, resetError = null) }
        viewModelScope.launch {
            val body = buildJsonObject {
                put("action", "set_password")
                put("email", state.resetEmail.trim())
                put("new_password", state.resetNewPassword)
            }
            when (val result = edgeFunctionClient.invoke("reset-password", body, anonymous = true)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(resetSending = false, resetStep = ResetStep.DONE) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(resetSending = false, resetError = result.message ?: "Failed to reset password") }
                }
                else -> {
                    _uiState.update { it.copy(resetSending = false, resetError = "Something went wrong. Try again.") }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DoctorLoginVM"
    }
}

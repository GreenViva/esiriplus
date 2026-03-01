package com.esiri.esiriplus.feature.auth.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.extensions.isValidEmail
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.usecase.LoginDoctorUseCase
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.DoctorProfileService
import com.esiri.esiriplus.feature.auth.biometric.BiometricAuthManager
import com.esiri.esiriplus.feature.auth.biometric.DeviceBindingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

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
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorLoginUiState())
    val uiState: StateFlow<DoctorLoginUiState> = _uiState.asStateFlow()

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

            var lastError: String? = null
            for (attempt in 1..MAX_LOGIN_ATTEMPTS) {
                when (val result = loginDoctor(email, password)) {
                    is Result.Success -> {
                        val session = result.data
                        val doctorId = session.user.id

                        // Check ban/suspension status before allowing navigation
                        val blocked = checkAccountStatus(session.accessToken, session.refreshToken, doctorId)
                        if (blocked) return@launch

                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                        return@launch
                    }
                    is Result.Error -> {
                        lastError = result.message ?: "Login failed"
                        Log.w(TAG, "Login attempt $attempt failed: $lastError")
                        if (attempt < MAX_LOGIN_ATTEMPTS) {
                            delay(RETRY_DELAY_MS)
                        }
                    }
                    is Result.Loading -> Unit
                }
            }
            _uiState.update { it.copy(isLoading = false, error = lastError) }
        }
    }

    /**
     * Checks if the doctor is banned or suspended by fetching their profile from Supabase.
     * Returns true if blocked (UI state is updated to show the appropriate screen).
     */
    private suspend fun checkAccountStatus(
        accessToken: String,
        refreshToken: String,
        doctorId: String,
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
                    Log.d(TAG, "Profile check: isBanned=${profile.isBanned}, suspendedUntil=${profile.suspendedUntil}, suspensionReason=${profile.suspensionReason}")

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

    fun clearBlockedState() {
        _uiState.update {
            DoctorLoginUiState()
        }
    }

    companion object {
        private const val TAG = "DoctorLoginVM"
        private const val MAX_LOGIN_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 1500L
    }
}

package com.esiri.esiriplus.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.esiri.esiriplus.core.common.extensions.isValidEmail
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.usecase.LoginDoctorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DoctorLoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val isFormValid: Boolean
        get() = email.isValidEmail() && password.length >= 6
}

@HiltViewModel
class DoctorLoginViewModel @Inject constructor(
    private val loginDoctor: LoginDoctorUseCase,
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
            _uiState.update { it.copy(isLoading = true, error = null) }
            val email = uiState.value.email
            val password = uiState.value.password

            // Retry once on transient failures (edge function cold starts, client init races)
            var lastError: String? = null
            for (attempt in 1..MAX_LOGIN_ATTEMPTS) {
                when (val result = loginDoctor(email, password)) {
                    is Result.Success -> {
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

    companion object {
        private const val TAG = "DoctorLoginVM"
        private const val MAX_LOGIN_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 1500L
    }
}

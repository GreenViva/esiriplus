package com.esiri.esiriplus.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.extensions.isValidEmail
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.usecase.LoginDoctorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
            when (val result = loginDoctor(uiState.value.email, uiState.value.password)) {
                is Result.Success -> onSuccess()
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message ?: "Login failed")
                }
                is Result.Loading -> Unit
            }
        }
    }
}

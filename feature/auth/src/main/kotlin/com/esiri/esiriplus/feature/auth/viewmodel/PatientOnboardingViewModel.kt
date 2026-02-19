package com.esiri.esiriplus.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.extensions.isValidKenyanPhone
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.usecase.CreatePatientSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientOnboardingUiState(
    val fullName: String = "",
    val phone: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val isFormValid: Boolean
        get() = fullName.isNotBlank() && phone.isValidKenyanPhone()
}

@HiltViewModel
class PatientOnboardingViewModel @Inject constructor(
    private val createPatientSession: CreatePatientSessionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientOnboardingUiState())
    val uiState: StateFlow<PatientOnboardingUiState> = _uiState.asStateFlow()

    fun onFullNameChanged(name: String) {
        _uiState.update { it.copy(fullName = name, error = null) }
    }

    fun onPhoneChanged(phone: String) {
        _uiState.update { it.copy(phone = phone, error = null) }
    }

    fun createSession(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = createPatientSession(uiState.value.phone, uiState.value.fullName)) {
                is Result.Success -> onSuccess()
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message ?: "An error occurred")
                }
                is Result.Loading -> Unit
            }
        }
    }
}

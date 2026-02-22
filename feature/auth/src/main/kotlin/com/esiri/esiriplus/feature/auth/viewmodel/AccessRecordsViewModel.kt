package com.esiri.esiriplus.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.model.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccessRecordsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessRecordsUiState())
    val uiState: StateFlow<AccessRecordsUiState> = _uiState.asStateFlow()

    fun onPatientIdChanged(value: String) {
        _uiState.update { it.copy(patientId = value, error = null) }
    }

    fun accessRecords() {
        val patientId = _uiState.value.patientId.trim()
        if (patientId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = authRepository.lookupPatientById(patientId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, isSuccess = true)
                    }
                }
                is Result.Error -> {
                    val message = when (val ex = result.exception) {
                        is ApiException -> ex.message
                        else -> null
                    } ?: "Patient ID not found. Please check your ID and try again."

                    _uiState.update {
                        it.copy(isLoading = false, error = message)
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}

data class AccessRecordsUiState(
    val patientId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
)

package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PatientHomeUiState(
    val isLoading: Boolean = false,
)

@HiltViewModel
class PatientHomeViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PatientHomeUiState())
    val uiState: StateFlow<PatientHomeUiState> = _uiState.asStateFlow()
}

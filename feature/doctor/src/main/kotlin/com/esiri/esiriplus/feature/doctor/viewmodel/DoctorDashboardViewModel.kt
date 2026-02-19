package com.esiri.esiriplus.feature.doctor.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DoctorDashboardUiState(
    val isLoading: Boolean = false,
)

@HiltViewModel
class DoctorDashboardViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(DoctorDashboardUiState())
    val uiState: StateFlow<DoctorDashboardUiState> = _uiState.asStateFlow()
}

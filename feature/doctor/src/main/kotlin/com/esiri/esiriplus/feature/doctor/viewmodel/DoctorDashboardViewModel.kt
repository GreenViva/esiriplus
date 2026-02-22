package com.esiri.esiriplus.feature.doctor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.DoctorProfileRepository
import com.esiri.esiriplus.core.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DoctorDashboardUiState(
    val isLoading: Boolean = true,
    val doctorName: String = "",
    val specialty: String = "",
    val isVerified: Boolean = false,
    val isOnline: Boolean = false,
    val pendingRequests: Int = 0,
    val activeConsultations: Int = 0,
    val todaysEarnings: String = "TSh 0",
    val totalPatients: Int = 0,
    val acceptanceRate: String = "\u2014",
    val isAvailable: Boolean = false,
)

@HiltViewModel
class DoctorDashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val doctorProfileRepository: DoctorProfileRepository,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorDashboardUiState())
    val uiState: StateFlow<DoctorDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDoctorProfile()
    }

    private fun loadDoctorProfile() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first()
            val userId = session?.user?.id ?: return@launch

            val profile = doctorProfileRepository.getDoctorById(userId)
            if (profile != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        doctorName = profile.fullName,
                        specialty = profile.specialty.name.lowercase()
                            .replaceFirstChar { c -> c.uppercase() }
                            .replace("_", " "),
                        isVerified = profile.isVerified,
                        isOnline = profile.isAvailable,
                        isAvailable = profile.isAvailable,
                    )
                }
            } else {
                // Fallback to session user data
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        doctorName = session.user.fullName,
                        isVerified = session.user.isVerified,
                    )
                }
            }
        }
    }

    fun onToggleOnline() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first() ?: return@launch
            val newState = !_uiState.value.isOnline
            doctorProfileRepository.updateAvailability(session.user.id, newState)
            _uiState.update { it.copy(isOnline = newState, isAvailable = newState) }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}

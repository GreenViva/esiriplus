package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ServiceTierDao
import com.esiri.esiriplus.core.database.entity.ServiceTierEntity
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServicesUiState(
    val services: List<ServiceTierEntity> = emptyList(),
    val selectedServiceId: String? = null,
    val isLoading: Boolean = true,
    val patientId: String = "",
)

@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val serviceTierDao: ServiceTierDao,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServicesUiState())
    val uiState: StateFlow<ServicesUiState> = _uiState.asStateFlow()

    init {
        loadServices()
        loadPatientId()
    }

    private fun loadServices() {
        viewModelScope.launch {
            serviceTierDao.getActiveServiceTiers().collect { tiers ->
                _uiState.update {
                    it.copy(services = tiers, isLoading = false)
                }
            }
        }
    }

    private fun loadPatientId() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first()
            if (session != null) {
                _uiState.update { it.copy(patientId = session.user.id) }
            }
        }
    }

    fun selectService(serviceId: String) {
        _uiState.update { it.copy(selectedServiceId = serviceId) }
    }
}

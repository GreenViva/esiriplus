package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ServiceTierDao
import com.esiri.esiriplus.core.database.entity.ServiceTierEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServicesUiState(
    val services: List<ServiceTierEntity> = emptyList(),
    val selectedServiceId: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val serviceTierDao: ServiceTierDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServicesUiState())
    val uiState: StateFlow<ServicesUiState> = _uiState.asStateFlow()

    init {
        loadServices()
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

    fun selectService(serviceId: String) {
        _uiState.update { it.copy(selectedServiceId = serviceId) }
    }
}

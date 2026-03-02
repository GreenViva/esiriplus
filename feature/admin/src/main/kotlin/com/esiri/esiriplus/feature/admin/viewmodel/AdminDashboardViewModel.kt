package com.esiri.esiriplus.feature.admin.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class StatsResponse(
    val stats: DoctorStats = DoctorStats(),
)

@Serializable
data class DoctorStats(
    val total: Int = 0,
    val pending: Int = 0,
    val active: Int = 0,
    val suspended: Int = 0,
    val rejected: Int = 0,
    val banned: Int = 0,
)

data class AdminDashboardUiState(
    val isLoading: Boolean = true,
    val stats: DoctorStats = DoctorStats(),
    val error: String? = null,
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = edgeFunctionClient.invoke("list-all-doctors")) {
                is ApiResult.Success -> {
                    try {
                        val response = json.decodeFromString<StatsResponse>(result.data)
                        _uiState.update {
                            it.copy(isLoading = false, stats = response.stats)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse stats", e)
                        _uiState.update {
                            it.copy(isLoading = false, error = "Failed to parse stats")
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Network error: ${result.message}")
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Unauthorized")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AdminDashboardVM"
    }
}

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
import kotlinx.coroutines.async
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

@Serializable
private data class AdminStatsResponse(
    val stats: AdminPlatformStats = AdminPlatformStats(),
)

@Serializable
data class AdminPlatformStats(
    val revenue: RevenueStats = RevenueStats(),
    @SerialName("doctor_earnings") val doctorEarnings: EarningsStats = EarningsStats(),
    val patients: PatientStats = PatientStats(),
    val consultations: ConsultationStats = ConsultationStats(),
)

@Serializable
data class RevenueStats(
    val total: Long = 0,
    @SerialName("platform_commission") val platformCommission: Long = 0,
    val pending: Long = 0,
    val currency: String = "TZS",
    @SerialName("completed_payments") val completedPayments: Int = 0,
    @SerialName("pending_payments") val pendingPayments: Int = 0,
    @SerialName("total_payments") val totalPayments: Int = 0,
)

@Serializable
data class EarningsStats(
    val total: Long = 0,
    val paid: Long = 0,
    val unpaid: Long = 0,
)

@Serializable
data class PatientStats(
    val total: Int = 0,
)

@Serializable
data class ConsultationStats(
    val total: Int = 0,
    val completed: Int = 0,
)

data class AdminDashboardUiState(
    val isLoading: Boolean = true,
    val stats: DoctorStats = DoctorStats(),
    val platformStats: AdminPlatformStats = AdminPlatformStats(),
    val platformError: String? = null,
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

            // Fetch doctor stats and platform stats in parallel
            val doctorsDeferred = async { edgeFunctionClient.invoke("list-all-doctors") }
            val platformDeferred = async { edgeFunctionClient.invoke("get-admin-stats") }

            val doctorsResult = doctorsDeferred.await()
            val platformResult = platformDeferred.await()

            // Parse doctor stats
            var doctorStats = DoctorStats()
            var error: String? = null
            when (doctorsResult) {
                is ApiResult.Success -> {
                    try {
                        doctorStats = json.decodeFromString<StatsResponse>(doctorsResult.data).stats
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse doctor stats", e)
                        error = "Failed to parse doctor stats"
                    }
                }
                is ApiResult.Error -> error = doctorsResult.message
                is ApiResult.NetworkError -> error = "Network error: ${doctorsResult.message}"
                is ApiResult.Unauthorized -> error = "Unauthorized"
            }

            // Parse platform stats
            var platformStats = AdminPlatformStats()
            var platformError: String? = null
            when (platformResult) {
                is ApiResult.Success -> {
                    Log.d(TAG, "Platform stats raw response: ${platformResult.data.take(500)}")
                    try {
                        platformStats = json.decodeFromString<AdminStatsResponse>(platformResult.data).stats
                        Log.d(TAG, "Platform stats parsed: revenue=${platformStats.revenue.total}, consultations=${platformStats.consultations.completed}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse platform stats", e)
                        platformError = "Failed to parse revenue data"
                    }
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Platform stats error: code=${platformResult.code}, msg=${platformResult.message}")
                    platformError = "Revenue unavailable: ${platformResult.message}"
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Platform stats network error: ${platformResult.message}")
                    platformError = "Revenue unavailable: network error"
                }
                is ApiResult.Unauthorized -> {
                    Log.w(TAG, "Platform stats unauthorized")
                    platformError = "Revenue unavailable: unauthorized"
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    stats = doctorStats,
                    platformStats = platformStats,
                    platformError = platformError,
                    error = error,
                )
            }
        }
    }

    companion object {
        private const val TAG = "AdminDashboardVM"
    }
}

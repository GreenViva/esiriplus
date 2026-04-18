package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.DoctorConsultationService
import com.esiri.esiriplus.core.network.service.UnsubmittedReportRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class UnsubmittedReportItem(
    val consultationId: String,
    val patientId: String,
    val chiefComplaint: String,
    val consultationType: String,
    val serviceTier: String,
    val endedAtMillis: Long,
)

data class DoctorUnsubmittedReportsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val items: List<UnsubmittedReportItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class DoctorUnsubmittedReportsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val consultationService: DoctorConsultationService,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorUnsubmittedReportsUiState())
    val uiState: StateFlow<DoctorUnsubmittedReportsUiState> = _uiState.asStateFlow()

    init {
        load(showSpinner = true)
    }

    fun refresh() {
        load(showSpinner = false)
    }

    private fun load(showSpinner: Boolean) {
        viewModelScope.launch {
            if (showSpinner) _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            else _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            val session = authRepository.currentSession.first()
            val doctorId = session?.user?.id
            if (doctorId == null) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, items = emptyList()) }
                return@launch
            }

            try {
                val freshAccess = tokenManager.getAccessTokenSync() ?: session.accessToken
                val freshRefresh = tokenManager.getRefreshTokenSync() ?: session.refreshToken
                supabaseClientProvider.importAuthToken(freshAccess, freshRefresh)
            } catch (e: Exception) {
                Log.w(TAG, "Auth import failed, continuing with current token", e)
            }

            when (val result = consultationService.getUnsubmittedReports(doctorId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            items = result.data.map(::toItem),
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message) }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = "Network error") }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = "Unauthorized") }
                }
            }
        }
    }

    private fun toItem(row: UnsubmittedReportRow): UnsubmittedReportItem {
        val endedMillis = runCatching { Instant.parse(row.sessionEndTime ?: row.updatedAt).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
        return UnsubmittedReportItem(
            consultationId = row.consultationId,
            patientId = row.patientSession?.patientId.orEmpty(),
            chiefComplaint = row.chiefComplaint.orEmpty(),
            consultationType = row.consultationType.orEmpty(),
            serviceTier = row.serviceTier,
            endedAtMillis = endedMillis,
        )
    }

    companion object {
        private const val TAG = "UnsubmittedReportsVM"
    }
}

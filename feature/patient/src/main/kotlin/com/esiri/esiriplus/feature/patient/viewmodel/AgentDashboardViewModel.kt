package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.AgentEarningsService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentDashboardUiState(
    val agentName: String = "",
    val isSignedOut: Boolean = false,
    val isCreatingSession: Boolean = false,
    val errorMessage: String? = null,
    /** Count of unpaid earnings rows — drives the badge on the Earnings card. */
    val pendingEarningsCount: Int = 0,
    /** Sum of unpaid earnings amounts (TZS). */
    val pendingEarningsAmount: Long = 0,
)

@HiltViewModel
class AgentDashboardViewModel @Inject constructor(
    private val application: Application,
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
    private val agentEarningsService: AgentEarningsService,
) : ViewModel() {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        AgentDashboardUiState(
            agentName = prefs.getString(KEY_AGENT_NAME, "Agent") ?: "Agent",
        ),
    )
    val uiState: StateFlow<AgentDashboardUiState> = _uiState.asStateFlow()

    /** Emitted once when patient session is ready and we can navigate to consultation flow. */
    private val _sessionReady = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionReady: SharedFlow<Unit> = _sessionReady.asSharedFlow()

    init {
        refreshEarningsSummary()
    }

    /**
     * Re-fetch pending badge numbers. Called on init and when the user
     * returns from the earnings screen so admin-side "mark paid" actions
     * are reflected without a sign-out/sign-in.
     */
    fun refreshEarningsSummary() {
        viewModelScope.launch {
            when (val result = agentEarningsService.getSummary()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        pendingEarningsCount = result.data.pendingCount,
                        pendingEarningsAmount = result.data.pendingAmount,
                    )
                }
                else -> {
                    // Badge is non-critical; swallow errors so a flaky network
                    // doesn't red-flag the dashboard.
                    Log.d(TAG, "Earnings summary refresh failed (non-fatal)")
                }
            }
        }
    }

    /**
     * Creates a patient session (anonymous) so the agent can go through the
     * consultation flow on behalf of a patient. The agent_id is already stored
     * in SharedPreferences and will be attached to the consultation request.
     */
    fun startConsultation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingSession = true, errorMessage = null) }

            // Save agent tokens so we can restore them later
            val agentAccessToken = tokenManager.getAccessTokenSync()
            val agentRefreshToken = tokenManager.getRefreshTokenSync()
            if (agentAccessToken != null && agentRefreshToken != null) {
                prefs.edit()
                    .putString(KEY_SAVED_ACCESS_TOKEN, agentAccessToken)
                    .putString(KEY_SAVED_REFRESH_TOKEN, agentRefreshToken)
                    .apply()
            }

            when (val result = authRepository.createPatientSession()) {
                is Result.Success -> {
                    Log.d(TAG, "Patient session created for agent consultation")
                    _uiState.update { it.copy(isCreatingSession = false) }
                    _sessionReady.tryEmit(Unit)
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to create patient session: ${result.message}")
                    // Restore agent tokens on failure
                    if (agentAccessToken != null && agentRefreshToken != null) {
                        tokenManager.saveTokens(agentAccessToken, agentRefreshToken,
                            System.currentTimeMillis() + 3600_000)
                    }
                    _uiState.update {
                        it.copy(
                            isCreatingSession = false,
                            errorMessage = "Failed to start consultation: ${result.message}",
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isCreatingSession = false) }
                }
            }
        }
    }

    fun signOut() {
        tokenManager.clearTokens()
        prefs.edit()
            .remove(KEY_AGENT_NAME)
            .remove(KEY_AGENT_ID)
            .remove(KEY_IS_AGENT)
            .remove(KEY_SAVED_ACCESS_TOKEN)
            .remove(KEY_SAVED_REFRESH_TOKEN)
            .apply()
        _uiState.update { it.copy(isSignedOut = true) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val TAG = "AgentDashboardVM"
        private const val PREFS_NAME = "agent_prefs"
        private const val KEY_AGENT_NAME = "agent_name"
        private const val KEY_AGENT_ID = "agent_id"
        private const val KEY_IS_AGENT = "is_agent"
        private const val KEY_SAVED_ACCESS_TOKEN = "saved_access_token"
        private const val KEY_SAVED_REFRESH_TOKEN = "saved_refresh_token"
    }
}

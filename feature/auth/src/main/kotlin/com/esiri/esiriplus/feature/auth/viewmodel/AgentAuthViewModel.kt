package com.esiri.esiriplus.feature.auth.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.model.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import javax.inject.Inject

data class AgentAuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val agentName: String = "",
)

@HiltViewModel
class AgentAuthViewModel @Inject constructor(
    private val application: Application,
    private val edgeFunctionClient: EdgeFunctionClient,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AgentAuthUiState())
    val uiState: StateFlow<AgentAuthUiState> = _uiState.asStateFlow()

    init {
        // Check if agent is already logged in
        val savedName = prefs.getString(KEY_AGENT_NAME, null)
        val hasToken = tokenManager.getAccessTokenSync() != null
        if (savedName != null && hasToken) {
            _uiState.update { it.copy(isAuthenticated = true, agentName = savedName) }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all fields") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val body = buildJsonObject {
                put("email", email.trim())
                put("password", password)
            }

            when (val result = edgeFunctionClient.invoke("login-agent", body, anonymous = true)) {
                is ApiResult.Success -> handleAuthSuccess(result.data)
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Network error. Please check your connection.")
                }
                is ApiResult.Unauthorized -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Invalid credentials.")
                }
            }
        }
    }

    fun signUp(
        name: String,
        mobile: String,
        email: String,
        residence: String,
        password: String,
    ) {
        if (name.isBlank() || mobile.isBlank() || email.isBlank() || residence.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in all fields") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val body = buildJsonObject {
                put("full_name", name.trim())
                put("mobile_number", mobile.trim())
                put("email", email.trim())
                put("place_of_residence", residence.trim())
                put("password", password)
            }

            when (val result = edgeFunctionClient.invoke("register-agent", body, anonymous = true)) {
                is ApiResult.Success -> handleAuthSuccess(result.data)
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Network error. Please check your connection.")
                }
                is ApiResult.Unauthorized -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Registration failed.")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun handleAuthSuccess(responseBody: String) {
        try {
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            val accessToken = jsonResponse["access_token"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing access_token")
            val refreshToken = jsonResponse["refresh_token"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing refresh_token")
            val expiresAt = jsonResponse["expires_at"]?.jsonPrimitive?.long
                ?: (System.currentTimeMillis() / 1000 + 3600)

            val user = jsonResponse["user"]?.jsonObject
            val agentName = user?.get("full_name")?.jsonPrimitive?.content
                ?: user?.get("email")?.jsonPrimitive?.content
                ?: "Agent"
            val agentUserId = user?.get("id")?.jsonPrimitive?.content

            // Convert expires_at (seconds) to millis
            val expiresAtMillis = expiresAt * 1000

            tokenManager.saveTokens(accessToken, refreshToken, expiresAtMillis)
            prefs.edit()
                .putString(KEY_AGENT_NAME, agentName)
                .putString(KEY_AGENT_ID, agentUserId)
                .putBoolean(KEY_IS_AGENT, true)
                .apply()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    agentName = agentName,
                    errorMessage = null,
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "Failed to process login response: ${e.message}")
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "agent_prefs"
        private const val KEY_AGENT_NAME = "agent_name"
        private const val KEY_AGENT_ID = "agent_id"
        private const val KEY_IS_AGENT = "is_agent"
    }
}

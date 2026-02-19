package com.esiri.esiriplus.feature.auth.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.IdempotencyKeyGenerator
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.dto.CreatePatientSessionRequest
import com.esiri.esiriplus.core.network.dto.SessionResponse
import com.esiri.esiriplus.core.network.model.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UnusedPrivateProperty")
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
    private val tokenManager: TokenManager,
) : AuthRepository {

    private val _currentSession = MutableStateFlow<Session?>(null)
    override val currentSession: Flow<Session?> = _currentSession

    override suspend fun createPatientSession(phone: String, fullName: String): Result<Session> {
        // TODO: Implement with edge function call
        return Result.Error(NotImplementedError("Not yet implemented"))
    }

    override suspend fun loginDoctor(email: String, password: String): Result<Session> {
        // TODO: Implement with edge function call
        return Result.Error(NotImplementedError("Not yet implemented"))
    }

    override suspend fun refreshSession(): Result<Session> {
        // TODO: Implement with edge function call
        return Result.Error(NotImplementedError("Not yet implemented"))
    }

    override suspend fun logout() {
        tokenManager.clearTokens()
        _currentSession.value = null
    }
}

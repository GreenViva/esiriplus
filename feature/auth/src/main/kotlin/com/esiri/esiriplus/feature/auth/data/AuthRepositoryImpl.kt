package com.esiri.esiriplus.feature.auth.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.IdempotencyKeyGenerator
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.dto.CreatePatientSessionRequest
import com.esiri.esiriplus.core.network.dto.RefreshTokenRequest
import com.esiri.esiriplus.core.network.dto.SessionResponse
import com.esiri.esiriplus.core.network.dto.toDomain
import com.esiri.esiriplus.core.network.model.toDomainResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
    private val tokenManager: TokenManager,
) : AuthRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val _currentSession = MutableStateFlow<Session?>(null)
    override val currentSession: Flow<Session?> = _currentSession

    override suspend fun createPatientSession(phone: String, fullName: String): Result<Session> {
        val request = CreatePatientSessionRequest(
            phone = phone,
            fullName = fullName,
            idempotencyKey = IdempotencyKeyGenerator.generate("patient-session"),
        )
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invokeAndDecode<SessionResponse>(
            functionName = "create-patient-session",
            body = body,
        )

        return apiResult.map { response ->
            saveSessionAndTokens(response)
        }.toDomainResult()
    }

    override suspend fun loginDoctor(email: String, password: String): Result<Session> {
        val body = Json.parseToJsonElement(
            """{"email":"$email","password":"$password"}""",
        ).jsonObject

        val apiResult = edgeFunctionClient.invokeAndDecode<SessionResponse>(
            functionName = "login-doctor",
            body = body,
        )

        return apiResult.map { response ->
            saveSessionAndTokens(response)
        }.toDomainResult()
    }

    override suspend fun refreshSession(): Result<Session> {
        val currentRefreshToken = tokenManager.getRefreshTokenSync()
            ?: return Result.Error(IllegalStateException("No refresh token available"))

        val request = RefreshTokenRequest(refreshToken = currentRefreshToken)
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invokeAndDecode<SessionResponse>(
            functionName = "refresh-session",
            body = body,
        )

        return apiResult.map { response ->
            saveSessionAndTokens(response)
        }.toDomainResult()
    }

    override suspend fun logout() {
        tokenManager.clearTokens()
        _currentSession.value = null
    }

    private fun saveSessionAndTokens(response: SessionResponse): Session {
        val session = response.toDomain()
        tokenManager.saveTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtMillis = response.expiresAt * SECONDS_TO_MILLIS,
        )
        _currentSession.value = session
        return session
    }

    companion object {
        private const val SECONDS_TO_MILLIS = 1000L
    }
}

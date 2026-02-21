package com.esiri.esiriplus.feature.auth.data

import com.esiri.esiriplus.core.common.di.IoDispatcher
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.IdempotencyKeyGenerator
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.dao.SessionDao
import com.esiri.esiriplus.core.database.dao.UserDao
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.SessionInvalidator
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.dto.PatientSessionResponse
import com.esiri.esiriplus.core.network.dto.RecoverPatientSessionRequest
import com.esiri.esiriplus.core.network.dto.RefreshTokenRequest
import com.esiri.esiriplus.core.network.dto.SessionResponse
import com.esiri.esiriplus.core.network.dto.SetupSecurityQuestionsRequest
import com.esiri.esiriplus.core.network.dto.toDomain
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.toDomainResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
    private val tokenManager: TokenManager,
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val database: EsiriplusDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository, SessionInvalidator {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    override val currentSession: Flow<Session?> =
        sessionDao.getCurrentSession().flatMapLatest { sessionEntity ->
            if (sessionEntity == null) {
                flowOf(null)
            } else {
                userDao.getUserById(sessionEntity.userId).map { userEntity ->
                    userEntity?.let { sessionEntity.toDomain(it.toDomain()) }
                }
            }
        }.flowOn(ioDispatcher)

    override suspend fun createPatientSession(): Result<Session> {
        val apiResult = edgeFunctionClient.invokeAndDecode<PatientSessionResponse>(
            functionName = "create-patient-session",
        )

        return when (apiResult) {
            is ApiResult.Success -> {
                val session = saveSessionAndTokens(apiResult.data)
                Result.Success(session)
            }
            else -> apiResult.map { error("unreachable") }.toDomainResult()
        }
    }

    private suspend fun saveSessionAndTokens(response: PatientSessionResponse): Session {
        val session = response.toDomain()
        tokenManager.saveTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtMillis = response.expiresIn * SECONDS_TO_MILLIS +
                System.currentTimeMillis(),
        )
        withContext(ioDispatcher) {
            userDao.insertUser(session.user.toEntity())
            sessionDao.insertSession(session.toEntity())
        }
        return session
    }

    override suspend fun loginDoctor(email: String, password: String): Result<Session> {
        val body = Json.parseToJsonElement(
            """{"email":"$email","password":"$password"}""",
        ).jsonObject

        return handleSessionResponse(
            edgeFunctionClient.invokeAndDecode<SessionResponse>(
                functionName = "login-doctor",
                body = body,
            ),
        )
    }

    override suspend fun refreshSession(): Result<Session> {
        val currentRefreshToken = tokenManager.getRefreshTokenSync()
            ?: return Result.Error(IllegalStateException("No refresh token available"))

        val request = RefreshTokenRequest(refreshToken = currentRefreshToken)
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        return handleSessionResponse(
            edgeFunctionClient.invokeAndDecode<SessionResponse>(
                functionName = "refresh-session",
                body = body,
            ),
        )
    }

    override suspend fun logout() {
        // Best-effort server-side revocation
        try {
            edgeFunctionClient.invoke("logout")
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Continue with local cleanup even if server call fails
        }

        tokenManager.clearTokens()
        withContext(ioDispatcher) {
            database.clearAllTables()
        }
    }

    override suspend fun recoverPatientSession(answers: Map<String, String>): Result<Session> {
        val request = RecoverPatientSessionRequest(
            answers = answers,
            idempotencyKey = IdempotencyKeyGenerator.generate("recover-patient"),
        )
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        return handleSessionResponse(
            edgeFunctionClient.invokeAndDecode<SessionResponse>(
                functionName = "recover-patient-session",
                body = body,
            ),
        )
    }

    override suspend fun setupSecurityQuestions(answers: Map<String, String>): Result<Unit> {
        val request = SetupSecurityQuestionsRequest(answers = answers)
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invoke(
            functionName = "setup-recovery",
            body = body,
        )

        return apiResult.map { }.toDomainResult()
    }

    override fun invalidate() {
        scope.launch {
            logout()
        }
    }

    private suspend fun handleSessionResponse(
        apiResult: ApiResult<SessionResponse>,
    ): Result<Session> {
        return when (apiResult) {
            is ApiResult.Success -> {
                val session = saveSessionAndTokens(apiResult.data)
                Result.Success(session)
            }
            else -> apiResult.map { error("unreachable") }.toDomainResult()
        }
    }

    private suspend fun saveSessionAndTokens(response: SessionResponse): Session {
        val session = response.toDomain()
        tokenManager.saveTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtMillis = response.expiresAt * SECONDS_TO_MILLIS,
        )
        withContext(ioDispatcher) {
            userDao.insertUser(session.user.toEntity())
            sessionDao.insertSession(session.toEntity())
        }
        return session
    }

    companion object {
        private const val SECONDS_TO_MILLIS = 1000L
    }
}

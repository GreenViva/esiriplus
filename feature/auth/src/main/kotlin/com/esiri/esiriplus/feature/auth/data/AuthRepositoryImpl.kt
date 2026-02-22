package com.esiri.esiriplus.feature.auth.data

import android.app.Application
import android.net.Uri
import com.esiri.esiriplus.core.common.di.IoDispatcher
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.IdempotencyKeyGenerator
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.dao.SessionDao
import com.esiri.esiriplus.core.database.dao.UserDao
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.domain.model.DoctorRegistration
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.SessionInvalidator
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.network.dto.DoctorLoginRequest
import com.esiri.esiriplus.core.network.dto.DoctorRegistrationRequest
import com.esiri.esiriplus.core.network.dto.LookupPatientRequest
import com.esiri.esiriplus.core.network.dto.PatientSessionResponse
import com.esiri.esiriplus.core.network.dto.RecoverByIdResponse
import com.esiri.esiriplus.core.network.dto.RecoverPatientSessionRequest
import com.esiri.esiriplus.core.network.dto.RefreshTokenRequest
import com.esiri.esiriplus.core.network.dto.SessionResponse
import com.esiri.esiriplus.core.network.dto.SetupSecurityQuestionsRequest
import com.esiri.esiriplus.core.network.dto.toDomain
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.toDomainResult
import com.esiri.esiriplus.core.network.storage.FileUploadService
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val application: Application,
    private val edgeFunctionClient: EdgeFunctionClient,
    private val tokenManager: TokenManager,
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val doctorProfileDao: DoctorProfileDao,
    private val database: EsiriplusDatabase,
    private val fileUploadService: FileUploadService,
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

    override suspend fun registerDoctor(registration: DoctorRegistration): Result<Session> {
        return withContext(ioDispatcher) {
            try {
                // 1. Upload files to Supabase Storage
                val tempId = UUID.randomUUID().toString()
                val profilePhotoUrl = uploadFileIfPresent(
                    registration.profilePhotoUri,
                    "$tempId/profile-photo",
                )
                val licenseDocumentUrl = uploadFileIfPresent(
                    registration.licenseDocumentUri,
                    "$tempId/license-document",
                )
                val certificatesUrl = uploadFileIfPresent(
                    registration.certificatesUri,
                    "$tempId/certificates",
                )

                // 2. Resolve specialty
                val specialty = if (registration.specialty == "Specialist" &&
                    registration.customSpecialty.isNotBlank()
                ) {
                    registration.customSpecialty
                } else {
                    registration.specialty
                }

                // 3. Build DTO and call edge function
                val request = DoctorRegistrationRequest(
                    email = registration.email,
                    password = registration.password,
                    fullName = registration.fullName,
                    countryCode = registration.countryCode,
                    phone = registration.phone,
                    specialty = specialty,
                    country = registration.country,
                    languages = registration.languages,
                    licenseNumber = registration.licenseNumber,
                    yearsExperience = registration.yearsExperience,
                    bio = registration.bio,
                    services = registration.services,
                    profilePhotoUrl = profilePhotoUrl,
                    licenseDocumentUrl = licenseDocumentUrl,
                    certificatesUrl = certificatesUrl,
                )
                val body = json.encodeToString(request)
                    .let { Json.parseToJsonElement(it).jsonObject }

                val apiResult = edgeFunctionClient.invokeAndDecode<SessionResponse>(
                    functionName = "register-doctor",
                    body = body,
                )

                when (apiResult) {
                    is ApiResult.Success -> {
                        val session = saveSessionAndTokens(apiResult.data)
                        // Cache doctor profile locally
                        val now = System.currentTimeMillis()
                        doctorProfileDao.insert(
                            DoctorProfileEntity(
                                doctorId = session.user.id,
                                fullName = registration.fullName,
                                email = registration.email,
                                phone = registration.phone,
                                specialty = specialty,
                                languages = registration.languages,
                                bio = registration.bio,
                                licenseNumber = registration.licenseNumber,
                                yearsExperience = registration.yearsExperience,
                                profilePhotoUrl = profilePhotoUrl,
                                createdAt = now,
                                updatedAt = now,
                                services = registration.services,
                                countryCode = registration.countryCode,
                                country = registration.country,
                                licenseDocumentUrl = licenseDocumentUrl,
                                certificatesUrl = certificatesUrl,
                            ),
                        )
                        Result.Success(session)
                    }
                    else -> apiResult.map { error("unreachable") }.toDomainResult()
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun loginDoctor(email: String, password: String): Result<Session> {
        val request = DoctorLoginRequest(email = email, password = password)
        val body = json.encodeToString(request)
            .let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invokeAndDecode<SessionResponse>(
            functionName = "login-doctor",
            body = body,
        )

        return when (apiResult) {
            is ApiResult.Success -> {
                val session = saveSessionAndTokens(apiResult.data)
                // Cache doctor profile from backend if available
                cacheDoctorProfile(session.user.id)
                Result.Success(session)
            }
            else -> apiResult.map { error("unreachable") }.toDomainResult()
        }
    }

    private suspend fun cacheDoctorProfile(doctorId: String) {
        try {
            // Best effort — dashboard will still work from the session user
            val existing = doctorProfileDao.getById(doctorId)
            if (existing != null) return
            // Profile will be fetched later by the dashboard if needed
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Non-critical
        }
    }

    private suspend fun uploadFileIfPresent(
        uriString: String?,
        storagePath: String,
    ): String? {
        if (uriString.isNullOrBlank()) return null

        val uri = Uri.parse(uriString)
        val bytes = readBytesFromUri(uri) ?: return null
        val contentType = application.contentResolver.getType(uri) ?: "application/octet-stream"

        val result = fileUploadService.uploadFile(
            bucketName = DOCTOR_DOCUMENTS_BUCKET,
            path = storagePath,
            bytes = bytes,
            contentType = contentType,
        )

        return when (result) {
            is ApiResult.Success -> {
                fileUploadService.getPublicUrl(DOCTOR_DOCUMENTS_BUCKET, result.data)
            }
            else -> null
        }
    }

    private fun readBytesFromUri(uri: Uri): ByteArray? {
        return try {
            application.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            null
        }
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
                functionName = "recover-by-questions",
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

    override suspend fun lookupPatientById(patientId: String): Result<Session> {
        // 1. Check local DB first (same device — patient already onboarded here)
        val localSession = tryLocalLookup(patientId)
        if (localSession != null) return Result.Success(localSession)

        // 2. Call recover-by-id edge function (new device or cleared data)
        val request = LookupPatientRequest(patientId = patientId)
        val body = json.encodeToString(request)
            .let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invokeAndDecode<RecoverByIdResponse>(
            functionName = "recover-by-id",
            body = body,
        )

        return when (apiResult) {
            is ApiResult.Success -> {
                val response = apiResult.data
                val session = Session(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresAt = Instant.parse(response.expiresAt),
                    user = User(
                        id = patientId.uppercase(),
                        fullName = "",
                        phone = "",
                        role = UserRole.PATIENT,
                        isVerified = false,
                    ),
                )
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
                Result.Success(session)
            }
            else -> apiResult.map { error("unreachable") }.toDomainResult()
        }
    }

    private suspend fun tryLocalLookup(patientId: String): Session? {
        return withContext(ioDispatcher) {
            val userEntity = userDao.getUserById(patientId).first() ?: return@withContext null
            val sessionEntity = sessionDao.getCurrentSession().first() ?: return@withContext null
            if (sessionEntity.userId != patientId) return@withContext null
            sessionEntity.toDomain(userEntity.toDomain())
        }
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
        private const val DOCTOR_DOCUMENTS_BUCKET = "doctor-documents"
    }
}

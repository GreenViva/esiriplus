package com.esiri.esiriplus.feature.auth.data

import android.app.Application
import android.net.Uri
import android.util.Log
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
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.network.dto.DeviceBindingCheckResponse
import com.esiri.esiriplus.core.network.dto.DeviceBindingRequest
import com.esiri.esiriplus.core.network.dto.DoctorLoginRequest
import com.esiri.esiriplus.core.network.dto.DoctorRegistrationRequest
import com.esiri.esiriplus.core.network.dto.LookupPatientRequest
import com.esiri.esiriplus.core.network.dto.PatientSessionResponse
import com.esiri.esiriplus.core.network.dto.RecoverByIdResponse
import com.esiri.esiriplus.core.network.dto.RecoverByQuestionsResponse
import com.esiri.esiriplus.core.network.dto.RecoverPatientSessionRequest
import com.esiri.esiriplus.core.network.dto.RefreshTokenRequest
import com.esiri.esiriplus.core.network.dto.SessionResponse
import com.esiri.esiriplus.core.network.dto.SetupSecurityQuestionsRequest
import com.esiri.esiriplus.core.network.dto.toDomain
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.toDomainResult
import com.esiri.esiriplus.core.network.storage.FileUploadService
import com.esiri.esiriplus.feature.auth.biometric.DeviceBindingManager
import java.time.Instant
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
    private val supabaseClientProvider: SupabaseClientProvider,
    private val deviceBindingManager: DeviceBindingManager,
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
                // 1. Resolve specialist_field (custom text when "Specialist" is chosen)
                val specialistField = if (registration.specialty == "Specialist" &&
                    registration.customSpecialty.isNotBlank()
                ) {
                    registration.customSpecialty
                } else {
                    null
                }

                // 2. Call edge function FIRST (creates account + profile, returns session)
                val request = DoctorRegistrationRequest(
                    email = registration.email,
                    password = registration.password,
                    fullName = registration.fullName,
                    countryCode = registration.countryCode,
                    phone = registration.phone,
                    specialty = registration.specialty,
                    country = registration.country,
                    languages = registration.languages,
                    licenseNumber = registration.licenseNumber,
                    yearsExperience = registration.yearsExperience,
                    bio = registration.bio,
                    services = registration.services,
                    specialistField = specialistField,
                    profilePhotoUrl = null,
                    licenseDocumentUrl = null,
                    certificatesUrl = null,
                )
                val body = json.encodeToString(request)
                    .let { Json.parseToJsonElement(it).jsonObject }

                val apiResult = edgeFunctionClient.invokeAndDecode<SessionResponse>(
                    functionName = "register-doctor",
                    body = body,
                )

                when (apiResult) {
                    is ApiResult.Success -> {
                        val response = apiResult.data
                        val session = saveSessionAndTokens(response)

                        // 3. Import auth token so Storage uploads are authenticated
                        try {
                            supabaseClientProvider.importAuthToken(
                                accessToken = response.accessToken,
                                refreshToken = response.refreshToken,
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to import auth token for file uploads", e)
                        }

                        // 4. Upload files (now authenticated)
                        val userId = session.user.id
                        val profilePhotoUrl = uploadFileIfPresent(
                            registration.profilePhotoUri,
                            PROFILE_PHOTOS_BUCKET,
                            "$userId/profile-photo",
                        )
                        val licenseDocumentUrl = uploadFileIfPresent(
                            registration.licenseDocumentUri,
                            CREDENTIALS_BUCKET,
                            "$userId/license-document",
                        )
                        val certificatesUrl = uploadFileIfPresent(
                            registration.certificatesUri,
                            CREDENTIALS_BUCKET,
                            "$userId/certificates",
                        )

                        // 5. Update server profile with file URLs if any were uploaded
                        if (profilePhotoUrl != null || licenseDocumentUrl != null ||
                            certificatesUrl != null
                        ) {
                            try {
                                fileUploadService.updateDoctorProfileUrls(
                                    doctorId = userId,
                                    profilePhotoUrl = profilePhotoUrl,
                                    licenseDocumentUrl = licenseDocumentUrl,
                                    certificatesUrl = certificatesUrl,
                                )
                                Log.d(TAG, "Updated doctor profile with file URLs")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to update profile with file URLs", e)
                            }
                        }

                        // 6. Cache doctor profile locally
                        val now = System.currentTimeMillis()
                        doctorProfileDao.insert(
                            DoctorProfileEntity(
                                doctorId = session.user.id,
                                fullName = registration.fullName,
                                email = registration.email,
                                phone = registration.phone,
                                specialty = registration.specialty,
                                specialistField = specialistField,
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

                        // 7. Bind device to this doctor (server-side)
                        bindDeviceOnServer(userId, deviceBindingManager.getDeviceFingerprint())

                        Result.Success(session)
                    }
                    else -> apiResult.map { error("unreachable") }.toDomainResult()
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "registerDoctor failed", e)
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

                // Check device binding on server (best-effort)
                checkDeviceBindingOnServer(
                    session.user.id,
                    deviceBindingManager.getDeviceFingerprint(),
                )

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
        bucketName: String,
        storagePath: String,
    ): String? {
        if (uriString.isNullOrBlank()) {
            Log.d(TAG, "uploadFileIfPresent: no URI for $bucketName/$storagePath")
            return null
        }

        val uri = Uri.parse(uriString)
        val bytes = readBytesFromUri(uri)
        if (bytes == null) {
            Log.w(TAG, "uploadFileIfPresent: failed to read bytes from $uri")
            return null
        }
        Log.d(TAG, "uploadFileIfPresent: read ${bytes.size} bytes for $bucketName/$storagePath")

        val contentType = application.contentResolver.getType(uri)
            ?: detectMimeType(bytes)
        Log.d(TAG, "uploadFileIfPresent: contentType=$contentType for $bucketName/$storagePath")

        val result = fileUploadService.uploadFile(
            bucketName = bucketName,
            path = storagePath,
            bytes = bytes,
            contentType = contentType,
        )

        return when (result) {
            is ApiResult.Success -> {
                val url = fileUploadService.getPublicUrl(bucketName, result.data)
                Log.d(TAG, "uploadFileIfPresent: uploaded to $url")
                url
            }
            else -> {
                Log.e(TAG, "uploadFileIfPresent: upload failed for $bucketName/$storagePath — $result")
                null
            }
        }
    }

    private fun detectMimeType(bytes: ByteArray): String {
        if (bytes.size >= 4) {
            // JPEG: FF D8
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
            // PNG: 89 50 4E 47
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
            ) return "image/png"
            // PDF: 25 50 44 46 (%PDF)
            if (bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
            ) return "application/pdf"
            // GIF: 47 49 46
            if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte()
            ) return "image/gif"
            // WEBP: 52 49 46 46 ... 57 45 42 50
            if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[8] == 0x57.toByte() &&
                bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte()
            ) return "image/webp"
        }
        return "image/jpeg" // safe default for doctor uploads
    }

    private fun readBytesFromUri(uri: Uri): ByteArray? {
        return try {
            application.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "readBytesFromUri failed for $uri", e)
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
            database.reseedReferenceData()
        }
    }

    override suspend fun recoverPatientSession(answers: Map<String, String>): Result<Session> {
        val request = RecoverPatientSessionRequest(
            answers = answers,
            idempotencyKey = IdempotencyKeyGenerator.generate("recover-patient"),
        )
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invokeAndDecode<RecoverByQuestionsResponse>(
            functionName = "recover-by-questions",
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
                        id = response.patientId,
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

    private suspend fun bindDeviceOnServer(doctorId: String, deviceFingerprint: String) {
        try {
            val request = DeviceBindingRequest(
                doctorId = doctorId,
                deviceFingerprint = deviceFingerprint,
            )
            val body = json.encodeToString(request)
                .let { Json.parseToJsonElement(it).jsonObject }
            edgeFunctionClient.invoke(
                functionName = "bind-device",
                body = body,
            )
            Log.d(TAG, "Device bound on server for doctor=$doctorId")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Failed to bind device on server (non-critical)", e)
        }
    }

    private suspend fun checkDeviceBindingOnServer(doctorId: String, deviceFingerprint: String) {
        try {
            val request = DeviceBindingRequest(
                doctorId = doctorId,
                deviceFingerprint = deviceFingerprint,
            )
            val body = json.encodeToString(request)
                .let { Json.parseToJsonElement(it).jsonObject }
            val result = edgeFunctionClient.invokeAndDecode<DeviceBindingCheckResponse>(
                functionName = "check-device-binding",
                body = body,
            )
            if (result is ApiResult.Success) {
                Log.d(
                    TAG,
                    "Device binding check: bound=${result.data.bound}, matches=${result.data.matches}",
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Failed to check device binding on server (non-critical)", e)
        }
    }

    companion object {
        private const val TAG = "AuthRepositoryImpl"
        private const val SECONDS_TO_MILLIS = 1000L
        private const val PROFILE_PHOTOS_BUCKET = "profile-photos"
        private const val CREDENTIALS_BUCKET = "credentials"
    }
}

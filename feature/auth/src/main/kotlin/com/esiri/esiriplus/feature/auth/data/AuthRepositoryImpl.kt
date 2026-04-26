package com.esiri.esiriplus.feature.auth.data

import android.app.Application
import android.net.Uri
import android.util.Log
import com.esiri.esiriplus.core.common.di.IoDispatcher
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.IdempotencyKeyGenerator
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.dao.PatientSessionDao
import com.esiri.esiriplus.core.database.dao.SessionDao
import com.esiri.esiriplus.core.database.dao.UserDao
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
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
import com.esiri.esiriplus.core.common.session.SessionBackup
import com.esiri.esiriplus.feature.auth.biometric.DeviceBindingManager
import com.esiri.esiriplus.core.network.interceptor.TokenRefresher
import kotlinx.coroutines.tasks.await
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val application: Application,
    private val edgeFunctionClient: EdgeFunctionClient,
    private val tokenManager: TokenManager,
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val patientSessionDao: PatientSessionDao,
    private val doctorProfileDao: DoctorProfileDao,
    private val database: EsiriplusDatabase,
    private val fileUploadService: FileUploadService,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val deviceBindingManager: DeviceBindingManager,
    private val tokenRefresher: TokenRefresher,
    private val sessionBackup: SessionBackup,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository, SessionInvalidator {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    @Volatile private var logoutInProgress = false

    override val currentSession: Flow<Session?> =
        sessionDao.getCurrentSession().flatMapLatest { sessionEntity ->
            if (sessionEntity == null) {
                flowOf(null)
            } else {
                userDao.getUserById(sessionEntity.userId).map { userEntity ->
                    userEntity?.let {
                        val session = sessionEntity.toDomain(it.toDomain())
                        // Ensure TokenManager has the token — EncryptedSharedPreferences
                        // on Samsung loses data, so restore from Room if needed.
                        tokenManager.restoreFromSession(
                            session.accessToken,
                            session.refreshToken,
                            session.expiresAt.toEpochMilli(),
                        )
                        session
                    }
                }
            }
        }.flowOn(ioDispatcher)

    override suspend fun createPatientSession(): Result<Session> {
        // Get FCM token so the server can send push notifications to this patient
        val fcmToken = try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .token.await()
        } catch (e: Exception) {
            Log.w("AuthRepo", "Failed to get FCM token", e)
            null
        }
        val body = if (fcmToken != null) {
            buildJsonObject {
                put("fcm_token", fcmToken)
            }
        } else null

        val apiResult = edgeFunctionClient.invokeAndDecode<PatientSessionResponse>(
            functionName = "create-patient-session",
            body = body,
            anonymous = true,
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
            // Seed patient_sessions row so location/demographics writers (like
            // LocationResolver and ConsultationRequestViewModel) have a row
            // to update. Without this they bail with "No session" because the
            // local table stays empty even after auth succeeds.
            val now = System.currentTimeMillis()
            patientSessionDao.insert(
                PatientSessionEntity(
                    sessionId = session.user.id,
                    sessionTokenHash = response.accessToken.hashCode().toString(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        // Plain-prefs backup — survives DB wipes, Keystore failures.
        sessionBackup.save(
            userId = session.user.id,
            role = session.user.role.name,
            fullName = session.user.fullName,
            email = session.user.email,
            isVerified = session.user.isVerified,
            refreshToken = response.refreshToken,
            accessToken = response.accessToken,
            expiresAtMillis = response.expiresIn * SECONDS_TO_MILLIS + System.currentTimeMillis(),
        )
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

                        // Steps 3-7 run in NonCancellable context because
                        // saving the session above triggers navigation which
                        // clears the ViewModel scope — without this, uploads
                        // and device binding get CancellationException.
                        withContext(NonCancellable) {
                            // 3. Import auth token so Storage uploads are authenticated
                            try {
                                supabaseClientProvider.importAuthToken(
                                    accessToken = response.accessToken,
                                    refreshToken = response.refreshToken,
                                )
                                Log.d(TAG, "importAuthToken succeeded")
                            } catch (e: Exception) {
                                Log.e(TAG, "importAuthToken FAILED — uploads will likely fail", e)
                            }

                            // 4. Upload files (now authenticated)
                            val userId = session.user.id
                            Log.d(
                                TAG,
                                "File URIs — photo=${registration.profilePhotoUri}" +
                                    " license=${registration.licenseDocumentUri}" +
                                    " certs=${registration.certificatesUri}",
                            )
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
                            Log.d(
                                TAG,
                                "Upload results — photo=$profilePhotoUrl" +
                                    " license=$licenseDocumentUrl certs=$certificatesUrl",
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
                                    Log.d(TAG, "Updated doctor profile with file URLs on server")
                                } catch (e: Exception) {
                                    Log.e(TAG, "FAILED to update profile URLs on server", e)
                                }
                            } else {
                                Log.w(TAG, "No files were uploaded — all URLs are null")
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

                            // Device binding is temporarily disabled.
                            // bindDeviceOnServer(userId, deviceBindingManager.getDeviceFingerprint())
                        }

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

                // Device binding is checked server-side by login-doctor.
                // Skipping client-side check — it triggers 401 from the
                // Supabase gateway (no --no-verify-jwt) which cascades into
                // TokenRefreshAuthenticator → session invalidation → logout.

                // Push FCM token so the server can send push notifications to this doctor.
                // onNewToken() fires before login, so the token isn't saved until here.
                pushFcmTokenToServer()

                Result.Success(session)
            }
            else -> apiResult.map { error("unreachable") }.toDomainResult()
        }
    }

    /**
     * Push the device's FCM token to the server so pushes can be delivered.
     *
     * Retries up to [maxAttempts] times with exponential backoff (1s, 2s, 4s)
     * because the old fire-and-forget version silently dropped failures on the
     * floor, leaving the doctor's `fcm_tokens` row empty and every subsequent
     * push silently dying at `send-push-notification.single()`. The cohort of
     * doctors bitten by this included Dr Gordian, who got auto-flagged for
     * "missing 3 consecutive requests" that his phone never actually received.
     *
     * Returns `true` if the server accepted the token on any attempt,
     * `false` if every attempt failed or if Firebase never handed us a
     * token. Callers that care (e.g. the doctor dashboard) can show a
     * "Notifications unavailable" banner and block go-online.
     */
    override suspend fun pushFcmTokenToServer(maxAttempts: Int): Boolean {
        repeat(maxAttempts) { attempt ->
            try {
                val fcmToken = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .token.await()
                if (fcmToken.isNullOrBlank()) {
                    Log.w(TAG, "FCM returned null/blank token on attempt ${attempt + 1}")
                } else {
                    val body = buildJsonObject { put("fcm_token", fcmToken) }
                    when (val result = edgeFunctionClient.invoke("update-fcm-token", body)) {
                        is ApiResult.Success -> {
                            Log.d(TAG, "FCM token pushed to server (attempt ${attempt + 1})")
                            return true
                        }
                        else -> {
                            Log.w(TAG, "update-fcm-token rejected on attempt ${attempt + 1}: $result")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM token push threw on attempt ${attempt + 1}", e)
            }
            // Exponential backoff between attempts, but not after the last one.
            if (attempt < maxAttempts - 1) {
                kotlinx.coroutines.delay(1_000L shl attempt) // 1s, 2s, 4s
            }
        }
        Log.e(TAG, "FCM token push failed after $maxAttempts attempts — doctor will not receive pushes")
        return false
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
            is ApiResult.Error -> {
                Log.e(
                    TAG,
                    "uploadFileIfPresent: API error for $bucketName/$storagePath" +
                        " — code=${result.code} message=${result.message}",
                )
                null
            }
            is ApiResult.NetworkError -> {
                Log.e(
                    TAG,
                    "uploadFileIfPresent: network error for $bucketName/$storagePath" +
                        " — ${result.message}",
                    result.exception,
                )
                null
            }
            is ApiResult.Unauthorized -> {
                Log.e(TAG, "uploadFileIfPresent: UNAUTHORIZED for $bucketName/$storagePath")
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
        // Try encrypted storage first, fall back to plain-prefs backup
        val currentRefreshToken = tokenManager.getRefreshTokenSync()
            ?: sessionBackup.refreshToken
            ?: return Result.Error(IllegalStateException("No refresh token available"))

        val accessToken = tokenManager.getAccessTokenSync()
        val isPatient = (accessToken != null &&
            com.esiri.esiriplus.core.network.interceptor.JwtUtils.isPatientToken(accessToken)) ||
            sessionBackup.userRole == "PATIENT"

        return if (isPatient) {
            refreshPatientSession(currentRefreshToken, accessToken)
        } else {
            refreshDoctorSession(currentRefreshToken)
        }
    }

    /**
     * Patient refresh: uses the dedicated edge function which also syncs FCM tokens.
     */
    private suspend fun refreshPatientSession(
        currentRefreshToken: String,
        accessToken: String?,
    ): Result<Session> {
        // Extract session_id from JWT, Room, or backup — need it for refresh endpoint.
        val sessionId = (if (accessToken != null) extractClaimFromJwt(accessToken, "session_id") else null)
            ?: withContext(ioDispatcher) { sessionDao.getCurrentSession().first()?.userId }
            ?: sessionBackup.userId

        val fcmToken = try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .token.await()
        } catch (_: Exception) { null }

        val request = RefreshTokenRequest(
            refreshToken = currentRefreshToken,
            sessionId = sessionId,
            fcmToken = fcmToken,
        )
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        // Use anonymous=true — the refresh token in the body is the credential.
        // Using patientAuth=true would send the EXPIRED access token in X-Patient-Token,
        // which validateAuth() rejects with 401 before the function body runs.
        return when (val apiResult = edgeFunctionClient.invokeAndDecode<PatientSessionResponse>(
            functionName = "refresh-patient-session",
            body = body,
            anonymous = true,
        )) {
            is ApiResult.Success -> {
                val session = saveSessionAndTokens(apiResult.data)
                Result.Success(session)
            }
            else -> apiResult.map { error("unreachable") }.toDomainResult()
        }
    }

    /**
     * Doctor refresh: uses the native Supabase /auth/v1/token endpoint directly
     * (no edge function needed) and updates the Room session so
     * ObserveAuthStateUseCase sees the fresh expiresAt.
     */
    private suspend fun refreshDoctorSession(currentRefreshToken: String): Result<Session> {
        return try {
            val success = withContext(ioDispatcher) {
                tokenRefresher.refreshToken(currentRefreshToken)
            }
            if (!success) {
                Log.w(TAG, "Doctor token refresh failed (invalid/revoked refresh token)")
                return Result.Error(IllegalStateException("Token refresh failed"))
            }

            // TokenRefresher already updated EncryptedSharedPreferences with
            // new access_token, refresh_token, and expires_at. Now sync Room
            // so ObserveAuthStateUseCase emits Authenticated instead of SessionExpired.
            val newAccessToken = tokenManager.getAccessTokenSync()
                ?: return Result.Error(IllegalStateException("No access token after refresh"))
            val newRefreshToken = tokenManager.getRefreshTokenSync()
                ?: return Result.Error(IllegalStateException("No refresh token after refresh"))
            val newExpiresAtMillis = tokenManager.getExpiresAtMillis()
            val newExpiresAt = Instant.ofEpochMilli(newExpiresAtMillis)

            // Try to update the existing session entity, OR reconstruct from JWT
            // if the DB was wiped (migration, downgrade, corruption).
            val existingSession = withContext(ioDispatcher) {
                sessionDao.getCurrentSession().first()
            }

            if (existingSession != null) {
                // Fast path: update existing session
                val updatedEntity = existingSession.copy(
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken,
                    expiresAt = newExpiresAt,
                )
                withContext(ioDispatcher) {
                    sessionDao.insertSession(updatedEntity)
                }

                val userEntity = withContext(ioDispatcher) {
                    userDao.getUserById(existingSession.userId).first()
                }
                if (userEntity != null) {
                    val session = updatedEntity.toDomain(userEntity.toDomain())
                    sessionBackup.save(
                        userId = session.user.id,
                        role = session.user.role.name,
                        fullName = session.user.fullName,
                        email = session.user.email,
                        isVerified = session.user.isVerified,
                        refreshToken = newRefreshToken,
                        accessToken = newAccessToken,
                        expiresAtMillis = newExpiresAtMillis,
                    )
                    Log.d(TAG, "Doctor session refreshed (existing session updated)")
                    return Result.Success(session)
                }
            }

            // Slow path: DB was wiped — reconstruct from backup + JWT
            Log.w(TAG, "No session in Room after refresh — reconstructing from backup")
            val userId = sessionBackup.userId
                ?: extractClaimFromJwt(newAccessToken, "sub")
                ?: return Result.Error(IllegalStateException("Cannot determine user ID"))

            val user = User(
                id = userId,
                fullName = sessionBackup.userName ?: "",
                phone = "",
                email = sessionBackup.userEmail,
                role = UserRole.DOCTOR,
                isVerified = sessionBackup.isVerified,
            )
            val session = Session(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                expiresAt = newExpiresAt,
                user = user,
            )
            withContext(ioDispatcher) {
                userDao.insertUser(user.toEntity())
                sessionDao.insertSession(session.toEntity())
            }
            // Update the plain-prefs backup with fresh token
            sessionBackup.save(
                userId = user.id,
                role = user.role.name,
                fullName = user.fullName,
                email = user.email,
                isVerified = user.isVerified,
                refreshToken = newRefreshToken,
                accessToken = newAccessToken,
                expiresAtMillis = newExpiresAtMillis,
            )
            Log.d(TAG, "Doctor session reconstructed from backup")
            Result.Success(session)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Doctor token refresh exception", e)
            Result.Error(e)
        }
    }


    private fun extractClaimFromJwt(token: String, claim: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
            )
            org.json.JSONObject(payload).optString(claim, null)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deletePatientAccount(): Boolean {
        // Server-side soft-delete first — a 30-day purge clock starts on the
        // patient_sessions row. Any failure here still falls through to the
        // local logout so the device is wiped regardless.
        var serverOk = false
        try {
            val result = edgeFunctionClient.invoke("delete-patient-account")
            serverOk = result is com.esiri.esiriplus.core.network.model.ApiResult.Success
            if (!serverOk) {
                Log.w(TAG, "delete-patient-account returned non-success: $result")
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "delete-patient-account call failed", e)
        }

        logout()
        return serverOk
    }

    override suspend fun logout() {
        if (logoutInProgress) {
            Log.w(TAG, "logout() already in progress — skipping duplicate call")
            return
        }
        logoutInProgress = true
        Log.d(TAG, "logout() started")
        try {
            // Best-effort server-side cleanup (deletes FCM token, marks offline, revokes session).
            // Catch everything — a 401 here must NOT trigger the authenticator → invalidate()
            // → logout() loop. The local cleanup below is what actually matters.
            try {
                edgeFunctionClient.invoke("logout")
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                // Continue with local cleanup even if server call fails
            }

            // Delete the FCM token from Firebase so this device stops receiving
            // pushes for the old account entirely. A fresh token is generated
            // on next login via fetchAndRegisterToken().
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken().await()
                Log.d(TAG, "Firebase token deleted on logout")
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                Log.w(TAG, "Failed to delete Firebase token (non-critical)")
            }

            tokenManager.clearTokens()
            sessionBackup.clear()
            withContext(ioDispatcher) {
                database.clearAllTables()
                database.reseedReferenceData()
            }
            Log.d(TAG, "logout() completed")
        } finally {
            logoutInProgress = false
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
            anonymous = true,
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
            patientAuth = true,
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
            anonymous = true,
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
        if (logoutInProgress) return
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
        // Plain-prefs backup — survives DB wipes, Keystore failures, everything.
        sessionBackup.save(
            userId = session.user.id,
            role = session.user.role.name,
            fullName = session.user.fullName,
            email = session.user.email,
            isVerified = session.user.isVerified,
            refreshToken = response.refreshToken,
            accessToken = response.accessToken,
            expiresAtMillis = response.expiresAt * SECONDS_TO_MILLIS,
        )
        return session
    }

    private suspend fun bindDeviceOnServer(doctorId: String, deviceFingerprint: String) {
        try {
            Log.d(TAG, "bindDevice: doctorId=$doctorId fingerprint=$deviceFingerprint")
            val request = DeviceBindingRequest(
                doctorId = doctorId,
                deviceFingerprint = deviceFingerprint,
            )
            val body = json.encodeToString(request)
                .let { Json.parseToJsonElement(it).jsonObject }
            val result = edgeFunctionClient.invoke(
                functionName = "bind-device",
                body = body,
            )
            Log.d(TAG, "bindDevice: result=$result")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "bindDevice FAILED for doctor=$doctorId", e)
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

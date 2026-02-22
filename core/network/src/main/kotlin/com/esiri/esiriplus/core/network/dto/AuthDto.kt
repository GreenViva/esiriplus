package com.esiri.esiriplus.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PatientSessionResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_expires_at") val refreshExpiresAt: String? = null,
    @SerialName("legacy_data_linked") val legacyDataLinked: Boolean = false,
)

@Serializable
data class SessionResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val phone: String,
    val email: String? = null,
    val role: String,
    @SerialName("is_verified") val isVerified: Boolean = false,
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class RecoverPatientSessionRequest(
    val answers: Map<String, String>,
    @SerialName("idempotency_key") val idempotencyKey: String,
)

@Serializable
data class SetupSecurityQuestionsRequest(
    val answers: Map<String, String>,
)

@Serializable
data class SetupRecoveryResponse(
    @SerialName("patient_id") val patientId: String,
    val warning: String? = null,
    @SerialName("questions_set") val questionsSet: Int? = null,
    @SerialName("already_setup") val alreadySetup: Boolean = false,
)

@Serializable
data class DoctorRegistrationRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("country_code") val countryCode: String,
    @SerialName("phone") val phone: String,
    @SerialName("specialty") val specialty: String,
    @SerialName("country") val country: String,
    @SerialName("languages") val languages: List<String>,
    @SerialName("license_number") val licenseNumber: String,
    @SerialName("years_experience") val yearsExperience: Int,
    @SerialName("bio") val bio: String,
    @SerialName("services") val services: List<String>,
    @SerialName("specialist_field") val specialistField: String? = null,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
    @SerialName("license_document_url") val licenseDocumentUrl: String? = null,
    @SerialName("certificates_url") val certificatesUrl: String? = null,
)

@Serializable
data class DoctorLoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
)

@Serializable
data class LookupPatientRequest(
    @SerialName("patient_id") val patientId: String,
)

@Serializable
data class RecoverByIdResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_expires_at") val refreshExpiresAt: String? = null,
    val message: String? = null,
)

package com.esiri.esiriplus.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreatePatientSessionRequest(
    val phone: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
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

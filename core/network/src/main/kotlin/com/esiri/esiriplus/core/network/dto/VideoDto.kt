package com.esiri.esiriplus.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoTokenRequest(
    @SerialName("consultation_id") val consultationId: String,
    @SerialName("call_type") val callType: String? = null,
    @SerialName("room_id") val roomId: String? = null,
)

@Serializable
data class VideoTokenResponse(
    val token: String,
    @SerialName("room_id") val roomId: String,
    val permissions: List<String> = emptyList(),
    @SerialName("expires_in") val expiresIn: Int = 7200,
)

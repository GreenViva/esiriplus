package com.esiri.esiriplus.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoTokenRequest(
    @SerialName("consultation_id") val consultationId: String,
)

@Serializable
data class VideoTokenResponse(
    val token: String,
    @SerialName("room_id") val roomId: String,
)

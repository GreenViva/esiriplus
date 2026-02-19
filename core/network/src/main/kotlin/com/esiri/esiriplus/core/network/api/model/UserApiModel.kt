package com.esiri.esiriplus.core.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserApiModel(
    val id: String,
    @Json(name = "full_name") val fullName: String,
    val phone: String,
    val email: String? = null,
    val role: String,
    @Json(name = "is_verified") val isVerified: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class UpdateUserBody(
    @Json(name = "full_name") val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

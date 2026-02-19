package com.esiri.esiriplus.core.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConsultationApiModel(
    val id: String,
    @Json(name = "patient_id") val patientId: String,
    @Json(name = "doctor_id") val doctorId: String? = null,
    @Json(name = "service_type") val serviceType: String,
    val status: String,
    val notes: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateConsultationStatusBody(
    val status: String,
)

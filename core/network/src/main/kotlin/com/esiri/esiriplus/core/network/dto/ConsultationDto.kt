package com.esiri.esiriplus.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateConsultationRequest(
    @SerialName("patient_id") val patientId: String,
    @SerialName("service_type") val serviceType: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
)

@Serializable
data class ConsultationResponse(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("doctor_id") val doctorId: String? = null,
    @SerialName("service_type") val serviceType: String,
    val status: String,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

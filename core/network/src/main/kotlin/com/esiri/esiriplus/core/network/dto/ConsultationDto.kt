package com.esiri.esiriplus.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateConsultationRequest(
    @SerialName("service_type") val serviceType: String,
    @SerialName("consultation_type") val consultationType: String,
    @SerialName("chief_complaint") val chiefComplaint: String,
    @SerialName("preferred_language") val preferredLanguage: String = "en",
    @SerialName("doctor_id") val doctorId: String? = null,
)

@Serializable
data class ConsultationResponse(
    @SerialName("consultation_id") val consultationId: String,
    val status: String,
    val message: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("available_slots") val availableSlots: Int? = null,
)

package com.esiri.esiriplus.core.domain.model

data class DoctorRating(
    val ratingId: String,
    val doctorId: String,
    val consultationId: String,
    val patientSessionId: String,
    val rating: Int,
    val comment: String? = null,
    val createdAt: Long,
    val synced: Boolean = false,
)

package com.esiri.esiriplus.core.database.relation

data class ConsultationWithDoctorInfo(
    val consultationId: String,
    val patientSessionId: String,
    val doctorId: String,
    val status: String,
    val serviceType: String,
    val consultationFee: Int,
    val sessionStartTime: Long?,
    val sessionEndTime: Long?,
    val sessionDurationMinutes: Int,
    val requestExpiresAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val fullName: String,
    val specialty: String,
    val averageRating: Double,
)

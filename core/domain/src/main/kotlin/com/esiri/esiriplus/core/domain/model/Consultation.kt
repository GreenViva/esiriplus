package com.esiri.esiriplus.core.domain.model

import java.time.Instant

data class Consultation(
    val id: String,
    val patientId: String,
    val doctorId: String? = null,
    val serviceType: ServiceType,
    val status: ConsultationStatus = ConsultationStatus.PENDING,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)

enum class ConsultationStatus {
    PENDING,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

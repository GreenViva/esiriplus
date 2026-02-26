package com.esiri.esiriplus.core.domain.model

data class ConsultationRequest(
    val requestId: String,
    val patientSessionId: String,
    val doctorId: String,
    val serviceType: String,
    val status: ConsultationRequestStatus,
    val createdAt: Long,
    val expiresAt: Long,
    /** Set when status transitions to ACCEPTED */
    val consultationId: String? = null,
)

enum class ConsultationRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    ;

    companion object {
        fun fromString(value: String): ConsultationRequestStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: PENDING
    }
}

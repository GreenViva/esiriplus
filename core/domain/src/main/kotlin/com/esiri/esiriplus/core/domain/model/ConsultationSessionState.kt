package com.esiri.esiriplus.core.domain.model

enum class ConsultationPhase {
    ACTIVE,
    AWAITING_EXTENSION,
    GRACE_PERIOD,
    COMPLETED,
}

data class ConsultationSessionState(
    val consultationId: String = "",
    val phase: ConsultationPhase = ConsultationPhase.ACTIVE,
    val remainingSeconds: Int = 0,
    val totalDurationMinutes: Int = 15,
    val originalDurationMinutes: Int = 15,
    val extensionCount: Int = 0,
    val serviceType: String = "",
    val consultationFee: Int = 0,
    val scheduledEndAtEpochMs: Long = 0L,
    val gracePeriodEndAtEpochMs: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    val extensionRequested: Boolean = false,
    val patientDeclined: Boolean = false,
)

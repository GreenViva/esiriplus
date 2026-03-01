package com.esiri.esiriplus.core.domain.model

data class Appointment(
    val appointmentId: String,
    val doctorId: String,
    val patientSessionId: String,
    val scheduledAt: Long,
    val durationMinutes: Int = 15,
    val status: AppointmentStatus = AppointmentStatus.BOOKED,
    val serviceType: String,
    val consultationType: String = "chat",
    val chiefComplaint: String = "",
    val consultationFee: Int = 0,
    val consultationId: String? = null,
    val rescheduledFrom: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class AppointmentStatus {
    BOOKED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    MISSED,
    CANCELLED,
    RESCHEDULED,
    ;

    companion object {
        fun fromString(value: String): AppointmentStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: BOOKED
    }
}

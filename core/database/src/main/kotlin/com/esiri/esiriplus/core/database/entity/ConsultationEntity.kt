package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "consultations",
    indices = [
        Index("patientSessionId"),
        Index("doctorId"),
        Index("status", "createdAt"),
    ],
)
data class ConsultationEntity(
    @PrimaryKey val consultationId: String,
    val patientSessionId: String,
    val doctorId: String,
    val status: String,
    val serviceType: String,
    val consultationFee: Int,
    val sessionStartTime: Long? = null,
    val sessionEndTime: Long? = null,
    val sessionDurationMinutes: Int = DEFAULT_SESSION_DURATION,
    val requestExpiresAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val DEFAULT_SESSION_DURATION = 15
    }
}

package com.esiri.esiriplus.core.database.entity

import androidx.room.ColumnInfo
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
    val scheduledEndAt: Long? = null,
    val extensionCount: Int = 0,
    val gracePeriodEndAt: Long? = null,
    val originalDurationMinutes: Int = DEFAULT_SESSION_DURATION,
    // ── Tier system (DB v26) ──────────────────────────────────────────────────
    @ColumnInfo(defaultValue = "ECONOMY") val serviceTier: String = "ECONOMY",
    @ColumnInfo(defaultValue = "TANZANIA") val serviceRegion: String = "TANZANIA",
    val followUpExpiry: Long? = null,
    @ColumnInfo(defaultValue = "0") val isPremium: Boolean = false,
    val parentConsultationId: String? = null,
) {
    companion object {
        const val DEFAULT_SESSION_DURATION = 15
    }
}

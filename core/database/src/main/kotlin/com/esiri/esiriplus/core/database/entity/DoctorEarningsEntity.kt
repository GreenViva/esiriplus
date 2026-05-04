package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctor_earnings",
    foreignKeys = [
        ForeignKey(
            entity = DoctorProfileEntity::class,
            parentColumns = ["doctorId"],
            childColumns = ["doctorId"],
            onDelete = ForeignKey.CASCADE,
        ),
        // No FK on consultationId — medication_reminder and
        // royal_checkin_escalation earnings reference *another* doctor's
        // consultation (e.g. a nurse earning for ringing a Royal patient
        // whose consultation belongs to the patient's primary doctor).
        // The earning is its own audit row; the consultation linkage is
        // informational and shouldn't gate insertion into the local cache.
    ],
    indices = [
        Index("doctorId", "status"),
        Index("consultationId"),
    ],
)
data class DoctorEarningsEntity(
    @PrimaryKey val earningId: String,
    val doctorId: String,
    val consultationId: String,
    val amount: Int,
    val status: String,
    val earningType: String = "consultation",
    val paidAt: Long? = null,
    val createdAt: Long,
)

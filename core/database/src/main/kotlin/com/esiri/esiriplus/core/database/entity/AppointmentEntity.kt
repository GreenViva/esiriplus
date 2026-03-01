package com.esiri.esiriplus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "appointments",
    indices = [
        Index("doctorId", "status"),
        Index("patientSessionId", "status"),
        Index("scheduledAt"),
        Index("status", "scheduledAt"),
    ],
)
data class AppointmentEntity(
    @PrimaryKey val appointmentId: String,
    val doctorId: String,
    val patientSessionId: String,
    val scheduledAt: Long,
    @ColumnInfo(defaultValue = "15") val durationMinutes: Int = 15,
    val status: String,
    val serviceType: String,
    @ColumnInfo(defaultValue = "chat") val consultationType: String = "chat",
    @ColumnInfo(defaultValue = "") val chiefComplaint: String = "",
    @ColumnInfo(defaultValue = "0") val consultationFee: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val consultationId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val rescheduledFrom: String? = null,
    @ColumnInfo(defaultValue = "NULL") val reminderSentAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "consultations")
data class ConsultationEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val doctorId: String?,
    val serviceType: String,
    val status: String,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant?,
)

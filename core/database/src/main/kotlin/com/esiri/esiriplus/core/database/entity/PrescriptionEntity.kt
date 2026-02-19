package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "prescriptions",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["id"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consultationId"), Index("doctorId")],
)
data class PrescriptionEntity(
    @PrimaryKey val id: String,
    val consultationId: String,
    val doctorId: String,
    val patientId: String,
    val medication: String,
    val dosage: String,
    val frequency: String,
    val duration: String,
    val notes: String? = null,
    val createdAt: Instant,
)

package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "diagnoses",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["consultationId"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consultationId")],
)
data class DiagnosisEntity(
    @PrimaryKey val id: String,
    val consultationId: String,
    val doctorId: String,
    val icdCode: String? = null,
    val description: String,
    val severity: String,
    val notes: String? = null,
    val createdAt: Instant,
)

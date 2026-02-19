package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "medical_records",
    indices = [Index("patientId")],
)
data class MedicalRecordEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val recordType: String,
    val title: String,
    val content: String,
    val metadata: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)

package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["consultationId"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consultationId"), Index("doctorId"), Index("patientId")],
)
data class ReviewEntity(
    @PrimaryKey val id: String,
    val consultationId: String,
    val patientId: String,
    val doctorId: String,
    val rating: Int,
    val comment: String? = null,
    val createdAt: Instant,
)

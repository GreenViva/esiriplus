package com.esiri.esiriplus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctor_ratings",
    foreignKeys = [
        ForeignKey(
            entity = DoctorProfileEntity::class,
            parentColumns = ["doctorId"],
            childColumns = ["doctorId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["consultationId"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PatientSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["patientSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("doctorId"),
        Index("consultationId", unique = true),
        Index("patientSessionId"),
    ],
)
data class DoctorRatingEntity(
    @PrimaryKey val ratingId: String,
    val doctorId: String,
    val consultationId: String,
    val patientSessionId: String,
    val rating: Int,
    val comment: String? = null,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
)

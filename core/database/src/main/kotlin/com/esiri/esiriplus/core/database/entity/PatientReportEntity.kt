package com.esiri.esiriplus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "patient_reports",
    foreignKeys = [
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
        Index("consultationId"),
        Index("patientSessionId"),
    ],
)
data class PatientReportEntity(
    @PrimaryKey val reportId: String,
    val consultationId: String,
    val patientSessionId: String,
    val reportUrl: String,
    val localFilePath: String? = null,
    val generatedAt: Long,
    val downloadedAt: Long? = null,
    val fileSizeBytes: Long,
    @ColumnInfo(defaultValue = "0") val isDownloaded: Boolean = false,
)

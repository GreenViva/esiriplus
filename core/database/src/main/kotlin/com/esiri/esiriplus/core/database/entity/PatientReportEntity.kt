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
    @ColumnInfo(defaultValue = "") val doctorName: String = "",
    @ColumnInfo(defaultValue = "0") val consultationDate: Long = 0L,
    @ColumnInfo(defaultValue = "") val patientAge: String = "",
    @ColumnInfo(defaultValue = "") val patientGender: String = "",
    @ColumnInfo(defaultValue = "") val diagnosedProblem: String = "",
    @ColumnInfo(defaultValue = "") val category: String = "",
    @ColumnInfo(defaultValue = "") val severity: String = "",
    @ColumnInfo(defaultValue = "") val presentingSymptoms: String = "",
    @ColumnInfo(defaultValue = "") val diagnosisAssessment: String = "",
    @ColumnInfo(defaultValue = "") val treatmentPlan: String = "",
    @ColumnInfo(defaultValue = "") val followUpInstructions: String = "",
    @ColumnInfo(defaultValue = "0") val followUpRecommended: Boolean = false,
    @ColumnInfo(defaultValue = "") val furtherNotes: String = "",
    @ColumnInfo(defaultValue = "") val verificationCode: String = "",
    @ColumnInfo(defaultValue = "") val prescribedMedications: String = "",
    @ColumnInfo(defaultValue = "[]") val prescriptionsJson: String = "[]",
)

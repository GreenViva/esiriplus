package com.esiri.esiriplus.core.domain.model

data class PatientReport(
    val reportId: String,
    val consultationId: String,
    val patientSessionId: String,
    val reportUrl: String,
    val localFilePath: String? = null,
    val generatedAt: Long,
    val downloadedAt: Long? = null,
    val fileSizeBytes: Long,
    val isDownloaded: Boolean = false,
)

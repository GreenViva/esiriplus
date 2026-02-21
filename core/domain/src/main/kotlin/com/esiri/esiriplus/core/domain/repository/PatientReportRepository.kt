package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.domain.model.PatientReport
import kotlinx.coroutines.flow.Flow

interface PatientReportRepository {
    suspend fun getReportById(reportId: String): PatientReport?
    fun getReportsByConsultation(consultationId: String): Flow<List<PatientReport>>
    fun getReportsByPatientSession(patientSessionId: String): Flow<List<PatientReport>>
    fun getDownloadedReports(): Flow<List<PatientReport>>
    suspend fun markAsDownloaded(reportId: String, localFilePath: String)
    suspend fun saveReport(report: PatientReport)
    suspend fun clearAll()
}

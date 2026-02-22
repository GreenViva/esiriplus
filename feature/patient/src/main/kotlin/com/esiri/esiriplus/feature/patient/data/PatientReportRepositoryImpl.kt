package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.database.dao.PatientReportDao
import com.esiri.esiriplus.core.database.entity.PatientReportEntity
import com.esiri.esiriplus.core.domain.model.PatientReport
import com.esiri.esiriplus.core.domain.repository.PatientReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientReportRepositoryImpl @Inject constructor(
    private val patientReportDao: PatientReportDao,
) : PatientReportRepository {

    override suspend fun getReportById(reportId: String): PatientReport? {
        return patientReportDao.getById(reportId)?.toDomain()
    }

    override fun getReportsByConsultation(consultationId: String): Flow<List<PatientReport>> {
        return patientReportDao.getByConsultationId(consultationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getReportsByPatientSession(patientSessionId: String): Flow<List<PatientReport>> {
        return patientReportDao.getByPatientSessionId(patientSessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDownloadedReports(): Flow<List<PatientReport>> {
        return patientReportDao.getDownloadedReports().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun markAsDownloaded(reportId: String, localFilePath: String) {
        patientReportDao.markAsDownloaded(
            reportId = reportId,
            localFilePath = localFilePath,
            downloadedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun saveReport(report: PatientReport) {
        patientReportDao.insert(report.toEntity())
    }

    override suspend fun clearAll() {
        patientReportDao.clearAll()
    }
}

private fun PatientReportEntity.toDomain(): PatientReport = PatientReport(
    reportId = reportId,
    consultationId = consultationId,
    patientSessionId = patientSessionId,
    reportUrl = reportUrl,
    localFilePath = localFilePath,
    generatedAt = generatedAt,
    downloadedAt = downloadedAt,
    fileSizeBytes = fileSizeBytes,
    isDownloaded = isDownloaded,
)

private fun PatientReport.toEntity(): PatientReportEntity = PatientReportEntity(
    reportId = reportId,
    consultationId = consultationId,
    patientSessionId = patientSessionId,
    reportUrl = reportUrl,
    localFilePath = localFilePath,
    generatedAt = generatedAt,
    downloadedAt = downloadedAt,
    fileSizeBytes = fileSizeBytes,
    isDownloaded = isDownloaded,
)

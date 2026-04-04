package com.esiri.esiriplus.feature.patient.data

import android.util.Log
import com.esiri.esiriplus.core.database.dao.PatientReportDao
import com.esiri.esiriplus.core.database.entity.PatientReportEntity
import com.esiri.esiriplus.core.domain.model.PatientReport
import com.esiri.esiriplus.core.domain.repository.PatientReportRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GetReportsResponse(
    val reports: List<ServerReport> = emptyList(),
)

@Serializable
private data class ServerReport(
    @SerialName("report_id") val reportId: String,
    @SerialName("consultation_id") val consultationId: String,
    @SerialName("doctor_name") val doctorName: String? = null,
    @SerialName("patient_session_id") val patientSessionId: String? = null,
    @SerialName("consultation_date") val consultationDate: String? = null,
    @SerialName("diagnosed_problem") val diagnosedProblem: String? = null,
    val category: String? = null,
    val severity: String? = null,
    @SerialName("presenting_symptoms") val presentingSymptoms: String? = null,
    val assessment: String? = null,
    val plan: String? = null,
    @SerialName("follow_up") val followUp: String? = null,
    @SerialName("follow_up_recommended") val followUpRecommended: Boolean? = null,
    @SerialName("further_notes") val furtherNotes: String? = null,
    @SerialName("treatment_plan") val treatmentPlan: String? = null,
    val history: String? = null,
    @SerialName("follow_up_plan") val followUpPlan: String? = null,
    val prescriptions: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("verification_code") val verificationCode: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Singleton
class PatientReportRepositoryImpl @Inject constructor(
    private val patientReportDao: PatientReportDao,
    private val edgeFunctionClient: EdgeFunctionClient,
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

    override suspend fun saveReports(reports: List<PatientReport>) {
        patientReportDao.insertAll(reports.map { it.toEntity() })
    }

    override suspend fun fetchReportsFromServer(): List<PatientReport> {
        return when (val result = edgeFunctionClient.invokeAndDecode<GetReportsResponse>(
            functionName = "get-patient-reports",
            patientAuth = true,
        )) {
            is ApiResult.Success -> {
                val reports = result.data.reports.map { it.toDomain() }
                Log.d(TAG, "Fetched ${reports.size} reports from server")
                reports
            }
            is ApiResult.Error -> {
                Log.e(TAG, "Failed to fetch reports: code=${result.code}, msg=${result.message}, details=${result.details}")
                emptyList()
            }
            is ApiResult.NetworkError -> {
                Log.e(TAG, "Network error fetching reports: ${result.message}")
                emptyList()
            }
            is ApiResult.Unauthorized -> {
                Log.e(TAG, "Unauthorized fetching reports")
                emptyList()
            }
        }
    }

    override fun observeReportById(reportId: String): Flow<PatientReport?> {
        return patientReportDao.observeById(reportId).map { it?.toDomain() }
    }

    override suspend fun clearAll() {
        patientReportDao.clearAll()
    }

    companion object {
        private const val TAG = "PatientReportRepo"
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
    doctorName = doctorName,
    consultationDate = consultationDate,
    diagnosedProblem = diagnosedProblem,
    category = category,
    severity = severity,
    presentingSymptoms = presentingSymptoms,
    diagnosisAssessment = diagnosisAssessment,
    treatmentPlan = treatmentPlan,
    followUpInstructions = followUpInstructions,
    followUpRecommended = followUpRecommended,
    furtherNotes = furtherNotes,
    verificationCode = verificationCode,
    prescribedMedications = prescribedMedications,
    prescriptionsJson = prescriptionsJson,
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
    doctorName = doctorName,
    consultationDate = consultationDate,
    diagnosedProblem = diagnosedProblem,
    category = category,
    severity = severity,
    presentingSymptoms = presentingSymptoms,
    diagnosisAssessment = diagnosisAssessment,
    treatmentPlan = treatmentPlan,
    followUpInstructions = followUpInstructions,
    followUpRecommended = followUpRecommended,
    furtherNotes = furtherNotes,
    verificationCode = verificationCode,
    prescribedMedications = prescribedMedications,
    prescriptionsJson = prescriptionsJson,
)

private fun ServerReport.toDomain(): PatientReport {
    val createdAtMillis = try {
        java.time.Instant.parse(createdAt ?: "").toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
    val consultationDateMillis = try {
        java.time.Instant.parse(consultationDate ?: "").toEpochMilli()
    } catch (_: Exception) {
        0L
    }
    return PatientReport(
        reportId = reportId,
        consultationId = consultationId,
        patientSessionId = patientSessionId ?: "",
        reportUrl = "",
        generatedAt = createdAtMillis,
        fileSizeBytes = 0L,
        doctorName = doctorName ?: "",
        consultationDate = consultationDateMillis,
        diagnosedProblem = diagnosedProblem ?: "",
        category = category ?: "",
        severity = severity ?: "",
        presentingSymptoms = presentingSymptoms ?: "",
        diagnosisAssessment = assessment ?: "",
        treatmentPlan = treatmentPlan ?: plan ?: "",
        followUpInstructions = followUpPlan ?: followUp ?: "",
        followUpRecommended = followUpRecommended ?: false,
        furtherNotes = furtherNotes ?: "",
        verificationCode = verificationCode ?: "",
        prescribedMedications = history ?: "",
        prescriptionsJson = prescriptions?.toString() ?: "[]",
    )
}

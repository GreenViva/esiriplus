package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.PatientReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientReportDao {

    @Query("SELECT * FROM patient_reports WHERE reportId = :reportId")
    suspend fun getById(reportId: String): PatientReportEntity?

    @Query("SELECT * FROM patient_reports WHERE consultationId = :consultationId ORDER BY generatedAt DESC")
    fun getByConsultationId(consultationId: String): Flow<List<PatientReportEntity>>

    @Query("SELECT * FROM patient_reports WHERE patientSessionId = :patientSessionId ORDER BY generatedAt DESC")
    fun getByPatientSessionId(patientSessionId: String): Flow<List<PatientReportEntity>>

    @Query("SELECT * FROM patient_reports WHERE isDownloaded = 1 ORDER BY downloadedAt DESC")
    fun getDownloadedReports(): Flow<List<PatientReportEntity>>

    @Query("UPDATE patient_reports SET isDownloaded = 1, localFilePath = :localFilePath, downloadedAt = :downloadedAt WHERE reportId = :reportId")
    suspend fun markAsDownloaded(reportId: String, localFilePath: String, downloadedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: PatientReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<PatientReportEntity>)

    @Delete
    suspend fun delete(report: PatientReportEntity)

    @Query("DELETE FROM patient_reports")
    suspend fun clearAll()
}

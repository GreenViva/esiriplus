package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.relation.ConsultationWithDoctor
import com.esiri.esiriplus.core.database.relation.ConsultationWithDoctorInfo
import com.esiri.esiriplus.core.database.relation.ConsultationWithMessages
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(consultation: ConsultationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(consultations: List<ConsultationEntity>)

    @Query("SELECT * FROM consultations WHERE consultationId = :id")
    suspend fun getById(id: String): ConsultationEntity?

    @Query("SELECT * FROM consultations WHERE patientSessionId = :sessionId ORDER BY createdAt DESC LIMIT 200")
    fun getByPatientSessionId(sessionId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE status = :status ORDER BY createdAt DESC LIMIT 200")
    fun getByStatus(status: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE doctorId = :doctorId ORDER BY createdAt DESC LIMIT 200")
    fun getByDoctorId(doctorId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE doctorId = :doctorId AND status = :status ORDER BY createdAt DESC LIMIT 200")
    fun getByDoctorIdAndStatus(doctorId: String, status: String): Flow<List<ConsultationEntity>>

    @Query(
        "SELECT * FROM consultations WHERE doctorId = :doctorId AND UPPER(serviceTier) = 'ROYAL' " +
            "AND (followUpExpiry IS NULL " +
            "OR followUpExpiry > :nowMillis " +
            "OR LOWER(status) IN ('active', 'awaiting_extension', 'grace_period')) " +
            "ORDER BY createdAt DESC LIMIT 200",
    )
    fun getRoyalConsultationsForDoctor(doctorId: String, nowMillis: Long = System.currentTimeMillis()): Flow<List<ConsultationEntity>>

    @Query(
        "SELECT * FROM consultations WHERE LOWER(status) IN ('active', 'awaiting_extension', 'grace_period') " +
            "ORDER BY createdAt DESC LIMIT 1",
    )
    fun getActiveConsultation(): Flow<ConsultationEntity?>

    @Query(
        "UPDATE consultations SET scheduledEndAt = :scheduledEndAt, extensionCount = :extensionCount, " +
            "gracePeriodEndAt = :gracePeriodEndAt, originalDurationMinutes = :originalDurationMinutes, " +
            "status = :status, serviceTier = :serviceTier, " +
            "doctorId = CASE WHEN :doctorId = '' THEN doctorId ELSE :doctorId END, " +
            "serviceType = CASE WHEN :serviceType = '' THEN serviceType ELSE :serviceType END, " +
            "updatedAt = :updatedAt WHERE consultationId = :consultationId",
    )
    suspend fun updateTimerState(
        consultationId: String,
        scheduledEndAt: Long?,
        extensionCount: Int,
        gracePeriodEndAt: Long?,
        originalDurationMinutes: Int,
        status: String,
        serviceTier: String,
        doctorId: String = "",
        serviceType: String = "",
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE consultations SET status = :status, updatedAt = :updatedAt WHERE consultationId = :consultationId")
    suspend fun updateStatus(consultationId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM consultations")
    suspend fun clearAll()

    @Transaction
    @Query("SELECT * FROM consultations WHERE consultationId = :id")
    fun getConsultationWithMessages(id: String): Flow<ConsultationWithMessages?>

    @Transaction
    @Query("SELECT * FROM consultations WHERE patientSessionId = :sessionId ORDER BY createdAt DESC LIMIT 200")
    fun getPatientConsultations(sessionId: String): Flow<List<ConsultationWithDoctor>>

    @Query(
        "SELECT c.consultationId, c.patientSessionId, c.doctorId, c.status, " +
            "c.serviceType, c.consultationFee, c.sessionStartTime, c.sessionEndTime, " +
            "c.sessionDurationMinutes, c.requestExpiresAt, c.createdAt, c.updatedAt, " +
            "c.scheduledEndAt, c.extensionCount, c.gracePeriodEndAt, c.originalDurationMinutes, " +
            "d.fullName, d.specialty, d.averageRating " +
            "FROM consultations c INNER JOIN doctor_profiles d ON c.doctorId = d.doctorId " +
            "WHERE c.patientSessionId = :sessionId ORDER BY c.createdAt DESC LIMIT 200",
    )
    fun getConsultationsWithDoctorInfo(sessionId: String): Flow<List<ConsultationWithDoctorInfo>>

    @Query(
        "SELECT c.* FROM consultations c " +
            "LEFT JOIN doctor_ratings r ON c.consultationId = r.consultationId " +
            "WHERE LOWER(c.status) = 'completed' AND r.ratingId IS NULL " +
            "AND c.patientSessionId = :patientSessionId " +
            "ORDER BY c.updatedAt DESC LIMIT 1",
    )
    suspend fun getUnratedCompletedConsultation(patientSessionId: String): ConsultationEntity?

    @Query(
        "SELECT d.fullName FROM consultations c " +
            "INNER JOIN doctor_profiles d ON c.doctorId = d.doctorId " +
            "WHERE c.consultationId = :consultationId LIMIT 1",
    )
    suspend fun getDoctorNameForConsultation(consultationId: String): String?

    /**
     * Ongoing consultations for the patient dashboard:
     * - Active/in-progress, OR
     * - Completed within follow-up window with remaining follow-ups
     *   (followUpMax=-1 = unlimited, followUpCount < followUpMax = has remaining)
     */
    @Query(
        "SELECT * FROM consultations " +
            "WHERE patientSessionId = :patientSessionId " +
            "AND (LOWER(status) IN ('active', 'in_progress', 'awaiting_extension', 'grace_period') " +
            "OR (LOWER(status) = 'completed' AND followUpExpiry > :currentTimeMillis " +
            "AND (followUpMax = -1 OR followUpCount < followUpMax))) " +
            "ORDER BY updatedAt DESC",
    )
    fun getOngoingConsultationsForPatient(
        patientSessionId: String,
        currentTimeMillis: Long,
    ): Flow<List<ConsultationEntity>>

    /**
     * Ongoing consultations for the doctor dashboard:
     * - Active/in-progress, OR
     * - Completed within follow-up window with remaining follow-ups
     */
    @Query(
        "SELECT * FROM consultations " +
            "WHERE doctorId = :doctorId " +
            "AND (LOWER(status) IN ('active', 'in_progress', 'awaiting_extension', 'grace_period') " +
            "OR (LOWER(status) = 'completed' AND followUpExpiry > :currentTimeMillis " +
            "AND (followUpMax = -1 OR followUpCount < followUpMax))) " +
            "ORDER BY updatedAt DESC",
    )
    fun getOngoingConsultationsForDoctor(
        doctorId: String,
        currentTimeMillis: Long,
    ): Flow<List<ConsultationEntity>>

    @Query("UPDATE consultations SET followUpExpiry = :followUpExpiry, updatedAt = :updatedAt WHERE consultationId = :consultationId")
    suspend fun setFollowUpExpiry(
        consultationId: String,
        followUpExpiry: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )
}

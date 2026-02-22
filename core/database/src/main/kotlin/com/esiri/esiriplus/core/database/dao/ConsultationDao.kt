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

    @Query("SELECT * FROM consultations WHERE patientSessionId = :sessionId ORDER BY createdAt DESC")
    fun getByPatientSessionId(sessionId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE doctorId = :doctorId ORDER BY createdAt DESC")
    fun getByDoctorId(doctorId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE doctorId = :doctorId AND status = :status ORDER BY createdAt DESC")
    fun getByDoctorIdAndStatus(doctorId: String, status: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    fun getActiveConsultation(): Flow<ConsultationEntity?>

    @Query("UPDATE consultations SET status = :status, updatedAt = :updatedAt WHERE consultationId = :consultationId")
    suspend fun updateStatus(consultationId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM consultations")
    suspend fun clearAll()

    @Transaction
    @Query("SELECT * FROM consultations WHERE consultationId = :id")
    fun getConsultationWithMessages(id: String): Flow<ConsultationWithMessages?>

    @Transaction
    @Query("SELECT * FROM consultations WHERE patientSessionId = :sessionId ORDER BY createdAt DESC")
    fun getPatientConsultations(sessionId: String): Flow<List<ConsultationWithDoctor>>

    @Query(
        "SELECT c.consultationId, c.patientSessionId, c.doctorId, c.status, " +
            "c.serviceType, c.consultationFee, c.sessionStartTime, c.sessionEndTime, " +
            "c.sessionDurationMinutes, c.requestExpiresAt, c.createdAt, c.updatedAt, " +
            "d.fullName, d.specialty, d.averageRating " +
            "FROM consultations c INNER JOIN doctor_profiles d ON c.doctorId = d.doctorId " +
            "WHERE c.patientSessionId = :sessionId ORDER BY c.createdAt DESC",
    )
    fun getConsultationsWithDoctorInfo(sessionId: String): Flow<List<ConsultationWithDoctorInfo>>
}

package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
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

    @Query("SELECT * FROM consultations WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    fun getActiveConsultation(): Flow<ConsultationEntity?>

    @Query("UPDATE consultations SET status = :status, updatedAt = :updatedAt WHERE consultationId = :consultationId")
    suspend fun updateStatus(consultationId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM consultations")
    suspend fun clearAll()
}

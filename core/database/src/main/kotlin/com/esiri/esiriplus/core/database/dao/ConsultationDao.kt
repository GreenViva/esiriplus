package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {
    @Query("SELECT * FROM consultations WHERE patientId = :patientId ORDER BY createdAt DESC")
    fun getConsultationsForPatient(patientId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE doctorId = :doctorId ORDER BY createdAt DESC")
    fun getConsultationsForDoctor(doctorId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE id = :id")
    suspend fun getConsultationById(id: String): ConsultationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(consultation: ConsultationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultations(consultations: List<ConsultationEntity>)
}

package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.VitalSignEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalSignDao {
    @Query("SELECT * FROM vital_signs WHERE consultationId = :consultationId ORDER BY recordedAt DESC")
    fun getForConsultation(consultationId: String): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs WHERE patientId = :patientId ORDER BY recordedAt DESC")
    fun getForPatient(patientId: String): Flow<List<VitalSignEntity>>

    @Query("SELECT * FROM vital_signs WHERE id = :id")
    suspend fun getById(id: String): VitalSignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vitalSign: VitalSignEntity)
}

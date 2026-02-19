package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.PatientProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientProfileDao {
    @Query("SELECT * FROM patient_profiles WHERE userId = :userId")
    fun getByUserId(userId: String): Flow<PatientProfileEntity?>

    @Query("SELECT * FROM patient_profiles WHERE id = :id")
    suspend fun getById(id: String): PatientProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: PatientProfileEntity)
}

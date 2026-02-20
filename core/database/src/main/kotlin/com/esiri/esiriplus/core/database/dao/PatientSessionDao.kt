package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: PatientSessionEntity)

    @Update
    suspend fun update(session: PatientSessionEntity)

    @Query("SELECT * FROM patient_sessions WHERE sessionId = :id")
    suspend fun getById(id: String): PatientSessionEntity?

    @Query("SELECT * FROM patient_sessions ORDER BY createdAt DESC LIMIT 1")
    fun getSession(): Flow<PatientSessionEntity?>

    @Query("DELETE FROM patient_sessions")
    suspend fun clearAll()
}

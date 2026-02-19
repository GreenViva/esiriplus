package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorProfileDao {
    @Query("SELECT * FROM doctor_profiles WHERE userId = :userId")
    fun getByUserId(userId: String): Flow<DoctorProfileEntity?>

    @Query("SELECT * FROM doctor_profiles WHERE id = :id")
    suspend fun getById(id: String): DoctorProfileEntity?

    @Query("SELECT * FROM doctor_profiles WHERE isAvailable = 1")
    fun getAvailableDoctors(): Flow<List<DoctorProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: DoctorProfileEntity)
}

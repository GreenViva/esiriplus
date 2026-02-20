package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.DoctorAvailabilityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorAvailabilityDao {

    @Query("SELECT * FROM doctor_availability WHERE doctorId = :doctorId")
    fun getByDoctorId(doctorId: String): Flow<DoctorAvailabilityEntity?>

    @Query("SELECT * FROM doctor_availability WHERE availabilityId = :availabilityId")
    suspend fun getById(availabilityId: String): DoctorAvailabilityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(availability: DoctorAvailabilityEntity)

    @Query("UPDATE doctor_availability SET isAvailable = :isAvailable, lastUpdated = :lastUpdated WHERE doctorId = :doctorId")
    suspend fun updateAvailability(doctorId: String, isAvailable: Boolean, lastUpdated: Long)

    @Query("DELETE FROM doctor_availability WHERE doctorId = :doctorId")
    suspend fun deleteByDoctorId(doctorId: String)
}

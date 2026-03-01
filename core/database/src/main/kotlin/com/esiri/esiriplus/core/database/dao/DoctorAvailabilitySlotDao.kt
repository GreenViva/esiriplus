package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.DoctorAvailabilitySlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorAvailabilitySlotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(slot: DoctorAvailabilitySlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(slots: List<DoctorAvailabilitySlotEntity>)

    @Query("SELECT * FROM doctor_availability_slots WHERE doctorId = :doctorId AND isActive = 1 ORDER BY dayOfWeek, startTime")
    fun getActiveByDoctorId(doctorId: String): Flow<List<DoctorAvailabilitySlotEntity>>

    @Query("SELECT * FROM doctor_availability_slots WHERE doctorId = :doctorId ORDER BY dayOfWeek, startTime")
    fun getAllByDoctorId(doctorId: String): Flow<List<DoctorAvailabilitySlotEntity>>

    @Query("SELECT * FROM doctor_availability_slots WHERE doctorId = :doctorId AND dayOfWeek = :dayOfWeek AND isActive = 1 ORDER BY startTime")
    fun getByDoctorIdAndDay(doctorId: String, dayOfWeek: Int): Flow<List<DoctorAvailabilitySlotEntity>>

    @Query("DELETE FROM doctor_availability_slots WHERE slotId = :slotId")
    suspend fun deleteById(slotId: String)

    @Query("DELETE FROM doctor_availability_slots WHERE doctorId = :doctorId")
    suspend fun deleteAllByDoctorId(doctorId: String)

    @Query("DELETE FROM doctor_availability_slots")
    suspend fun clearAll()
}

package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.DoctorEarningsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorEarningsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(earning: DoctorEarningsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(earnings: List<DoctorEarningsEntity>)

    @Query("SELECT * FROM doctor_earnings WHERE earningId = :earningId")
    suspend fun getById(earningId: String): DoctorEarningsEntity?

    @Query("SELECT * FROM doctor_earnings WHERE doctorId = :doctorId ORDER BY createdAt DESC")
    fun getEarningsForDoctor(doctorId: String): Flow<List<DoctorEarningsEntity>>

    @Query("SELECT * FROM doctor_earnings WHERE doctorId = :doctorId AND status = :status ORDER BY createdAt DESC")
    fun getEarningsByStatus(doctorId: String, status: String): Flow<List<DoctorEarningsEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM doctor_earnings WHERE doctorId = :doctorId AND createdAt >= :startDate AND createdAt <= :endDate")
    fun getTotalEarnings(doctorId: String, startDate: Long, endDate: Long): Flow<Int>

    @Delete
    suspend fun delete(earning: DoctorEarningsEntity)

    @Query("DELETE FROM doctor_earnings")
    suspend fun clearAll()
}

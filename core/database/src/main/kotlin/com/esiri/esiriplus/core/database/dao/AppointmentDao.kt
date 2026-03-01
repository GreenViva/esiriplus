package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appointment: AppointmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appointments: List<AppointmentEntity>)

    @Query("SELECT * FROM appointments WHERE appointmentId = :id")
    suspend fun getById(id: String): AppointmentEntity?

    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId ORDER BY scheduledAt DESC")
    fun getByDoctorId(doctorId: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE patientSessionId = :sessionId ORDER BY scheduledAt DESC")
    fun getByPatientSessionId(sessionId: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE doctorId = :doctorId AND status = :status ORDER BY scheduledAt ASC")
    fun getByDoctorIdAndStatus(doctorId: String, status: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE patientSessionId = :sessionId AND status = :status ORDER BY scheduledAt ASC")
    fun getByPatientSessionIdAndStatus(sessionId: String, status: String): Flow<List<AppointmentEntity>>

    @Query(
        "SELECT * FROM appointments WHERE doctorId = :doctorId " +
            "AND status IN ('booked', 'confirmed', 'in_progress') " +
            "AND scheduledAt >= :fromTime ORDER BY scheduledAt ASC",
    )
    fun getUpcomingByDoctorId(doctorId: String, fromTime: Long = System.currentTimeMillis()): Flow<List<AppointmentEntity>>

    @Query(
        "SELECT * FROM appointments WHERE patientSessionId = :sessionId " +
            "AND status IN ('booked', 'confirmed', 'in_progress') " +
            "AND scheduledAt >= :fromTime ORDER BY scheduledAt ASC",
    )
    fun getUpcomingByPatientSessionId(sessionId: String, fromTime: Long = System.currentTimeMillis()): Flow<List<AppointmentEntity>>

    @Query(
        "SELECT * FROM appointments WHERE doctorId = :doctorId " +
            "AND scheduledAt >= :dayStart AND scheduledAt < :dayEnd " +
            "ORDER BY scheduledAt ASC",
    )
    fun getByDoctorIdAndDay(doctorId: String, dayStart: Long, dayEnd: Long): Flow<List<AppointmentEntity>>

    @Query("UPDATE appointments SET status = :status, updatedAt = :updatedAt WHERE appointmentId = :appointmentId")
    suspend fun updateStatus(appointmentId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE appointments SET consultationId = :consultationId, status = 'in_progress', updatedAt = :updatedAt WHERE appointmentId = :appointmentId")
    suspend fun startSession(appointmentId: String, consultationId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM appointments")
    suspend fun clearAll()
}

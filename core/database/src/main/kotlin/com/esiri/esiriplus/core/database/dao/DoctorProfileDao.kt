package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.database.relation.DoctorWithCredentials
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorProfileDao {

    @Query("SELECT * FROM doctor_profiles")
    fun getAll(): Flow<List<DoctorProfileEntity>>

    @Query("SELECT * FROM doctor_profiles WHERE doctorId = :doctorId")
    suspend fun getById(doctorId: String): DoctorProfileEntity?

    @Query("SELECT * FROM doctor_profiles WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): DoctorProfileEntity?

    @Query(
        """
        SELECT * FROM doctor_profiles
        WHERE isBanned = 0
          AND (
              specialty = :specialty
              OR (:specialty = 'gp' AND specialty = 'specialist' AND canServeAsGp = 1)
          )
        """,
    )
    fun getBySpecialty(specialty: String): Flow<List<DoctorProfileEntity>>

    @Query(
        """
        DELETE FROM doctor_profiles
        WHERE doctorId NOT IN (:keepIds)
          AND (
              specialty = :specialty
              OR (:specialty = 'gp' AND specialty = 'specialist' AND canServeAsGp = 1)
          )
        """,
    )
    suspend fun deleteStaleBySpecialty(specialty: String, keepIds: List<String>)

    @Query("SELECT * FROM doctor_profiles WHERE isVerified = 1 AND isAvailable = 1")
    fun getAvailableDoctors(): Flow<List<DoctorProfileEntity>>

    @Query("SELECT * FROM doctor_profiles WHERE averageRating >= :minRating")
    fun getByRatingRange(minRating: Double): Flow<List<DoctorProfileEntity>>

    @Query("UPDATE doctor_profiles SET isAvailable = :isAvailable, updatedAt = :updatedAt WHERE doctorId = :doctorId")
    suspend fun updateAvailability(doctorId: String, isAvailable: Boolean, updatedAt: Long)

    @Query("UPDATE doctor_profiles SET averageRating = :averageRating, totalRatings = :totalRatings, updatedAt = :updatedAt WHERE doctorId = :doctorId")
    suspend fun updateRating(doctorId: String, averageRating: Double, totalRatings: Int, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: DoctorProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<DoctorProfileEntity>)

    /**
     * Atomically replaces cached doctors for a specialty: deletes stale rows and
     * inserts fresh ones in a single transaction so the Room Flow never emits
     * an empty intermediate state.
     */
    @Transaction
    suspend fun replaceBySpecialty(specialty: String, freshDoctors: List<DoctorProfileEntity>) {
        val keepIds = freshDoctors.map { it.doctorId }
        deleteStaleBySpecialty(specialty, keepIds)
        if (freshDoctors.isNotEmpty()) {
            insertAll(freshDoctors)
        }
    }

    @Delete
    suspend fun delete(profile: DoctorProfileEntity)

    @Query("DELETE FROM doctor_profiles")
    suspend fun clearAll()

    @Transaction
    @Query("SELECT * FROM doctor_profiles WHERE doctorId = :doctorId")
    fun getDoctorWithCredentials(doctorId: String): Flow<DoctorWithCredentials?>
}

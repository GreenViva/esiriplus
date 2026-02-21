package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.DoctorRatingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorRatingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rating: DoctorRatingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ratings: List<DoctorRatingEntity>)

    @Query("SELECT * FROM doctor_ratings WHERE ratingId = :ratingId")
    suspend fun getById(ratingId: String): DoctorRatingEntity?

    @Query("SELECT * FROM doctor_ratings WHERE doctorId = :doctorId ORDER BY createdAt DESC")
    fun getByDoctorId(doctorId: String): Flow<List<DoctorRatingEntity>>

    @Query("SELECT COALESCE(AVG(CAST(rating AS REAL)), 0.0) FROM doctor_ratings WHERE doctorId = :doctorId")
    fun getAverageRating(doctorId: String): Flow<Double>

    @Query("SELECT * FROM doctor_ratings WHERE synced = 0")
    suspend fun getUnsyncedRatings(): List<DoctorRatingEntity>

    @Query("UPDATE doctor_ratings SET synced = 1 WHERE ratingId = :ratingId")
    suspend fun markSynced(ratingId: String)

    @Delete
    suspend fun delete(rating: DoctorRatingEntity)

    @Query("DELETE FROM doctor_ratings")
    suspend fun clearAll()
}

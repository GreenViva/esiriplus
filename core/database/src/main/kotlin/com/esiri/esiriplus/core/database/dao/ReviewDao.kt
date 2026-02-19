package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE doctorId = :doctorId ORDER BY createdAt DESC")
    fun getForDoctor(doctorId: String): Flow<List<ReviewEntity>>

    @Query("SELECT AVG(CAST(rating AS REAL)) FROM reviews WHERE doctorId = :doctorId")
    fun getAverageRating(doctorId: String): Flow<Double?>

    @Query("SELECT * FROM reviews WHERE id = :id")
    suspend fun getById(id: String): ReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(review: ReviewEntity)
}

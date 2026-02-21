package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.TypingIndicatorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TypingIndicatorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(indicator: TypingIndicatorEntity)

    @Query("SELECT * FROM typing_indicators WHERE consultationId = :consultationId")
    fun getByConsultationId(consultationId: String): Flow<List<TypingIndicatorEntity>>

    @Query("DELETE FROM typing_indicators WHERE updatedAt < :threshold")
    suspend fun deleteOld(threshold: Long)

    @Query("DELETE FROM typing_indicators")
    suspend fun clearAll()
}

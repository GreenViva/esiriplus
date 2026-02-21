package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.VideoCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoCallDao {

    @Query("SELECT * FROM video_calls WHERE callId = :callId")
    suspend fun getById(callId: String): VideoCallEntity?

    @Query("SELECT * FROM video_calls WHERE consultationId = :consultationId ORDER BY createdAt DESC")
    fun getByConsultationId(consultationId: String): Flow<List<VideoCallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(videoCall: VideoCallEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videoCalls: List<VideoCallEntity>)

    @Delete
    suspend fun delete(videoCall: VideoCallEntity)

    @Query("DELETE FROM video_calls")
    suspend fun clearAll()
}

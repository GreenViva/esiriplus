package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE consultationId = :consultationId ORDER BY createdAt ASC")
    fun getByConsultationId(consultationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :id")
    suspend fun getById(id: String): MessageEntity?

    @Query("UPDATE messages SET isRead = 1 WHERE messageId = :messageId")
    suspend fun markAsRead(messageId: String)

    @Query("UPDATE messages SET synced = 1 WHERE messageId = :messageId")
    suspend fun markAsSynced(messageId: String)

    @Query("SELECT * FROM messages WHERE synced = 0")
    suspend fun getUnsyncedMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE synced = 0 AND consultationId = :consultationId")
    suspend fun getUnsyncedByConsultation(consultationId: String): List<MessageEntity>

    @Query("SELECT MAX(createdAt) FROM messages WHERE consultationId = :consultationId AND synced = 1")
    suspend fun getLatestSyncedTimestamp(consultationId: String): Long?

    @Query("UPDATE messages SET retryCount = retryCount + 1, failedAt = :failedAt WHERE messageId = :messageId")
    suspend fun incrementRetryCount(messageId: String, failedAt: Long)

    @Query("SELECT * FROM messages WHERE synced = 0 AND retryCount < :maxRetries ORDER BY createdAt ASC")
    suspend fun getRetryableMessages(maxRetries: Int): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}

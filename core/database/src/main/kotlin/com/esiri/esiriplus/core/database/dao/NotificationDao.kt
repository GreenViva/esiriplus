package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun getForUser(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE userId = :userId AND readAt IS NULL ORDER BY createdAt DESC")
    fun getUnreadNotifications(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND readAt IS NULL")
    fun getUnreadCount(userId: String): Flow<Int>

    @Query("SELECT * FROM notifications WHERE notificationId = :notificationId")
    suspend fun getById(notificationId: String): NotificationEntity?

    @Query("UPDATE notifications SET readAt = :readAt WHERE notificationId = :notificationId")
    suspend fun markAsRead(notificationId: String, readAt: Long)

    @Query("UPDATE notifications SET readAt = :readAt WHERE userId = :userId AND readAt IS NULL")
    suspend fun markAllAsRead(userId: String, readAt: Long)

    @Query("DELETE FROM notifications WHERE createdAt < :threshold")
    suspend fun deleteOld(threshold: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    @Delete
    suspend fun delete(notification: NotificationEntity)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

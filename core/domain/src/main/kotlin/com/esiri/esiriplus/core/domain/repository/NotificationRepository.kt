package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.domain.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotificationsForUser(userId: String): Flow<List<Notification>>
    fun getUnreadNotifications(userId: String): Flow<List<Notification>>
    fun getUnreadCount(userId: String): Flow<Int>
    suspend fun getNotificationById(notificationId: String): Notification?
    suspend fun markAsRead(notificationId: String)
    suspend fun markAllAsRead(userId: String)
    suspend fun deleteOldNotifications(threshold: Long)
    suspend fun clearAll()
}

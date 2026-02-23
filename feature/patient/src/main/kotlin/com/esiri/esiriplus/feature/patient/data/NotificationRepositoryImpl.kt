package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.database.dao.NotificationDao
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import com.esiri.esiriplus.core.domain.model.Notification
import com.esiri.esiriplus.core.domain.model.NotificationType
import com.esiri.esiriplus.core.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao,
) : NotificationRepository {

    override fun getNotificationsForUser(userId: String): Flow<List<Notification>> {
        return notificationDao.getForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUnreadNotifications(userId: String): Flow<List<Notification>> {
        return notificationDao.getUnreadNotifications(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUnreadCount(userId: String): Flow<Int> {
        return notificationDao.getUnreadCount(userId)
    }

    override suspend fun getNotificationById(notificationId: String): Notification? {
        return notificationDao.getById(notificationId)?.toDomain()
    }

    override suspend fun markAsRead(notificationId: String) {
        notificationDao.markAsRead(notificationId, System.currentTimeMillis())
    }

    override suspend fun markAllAsRead(userId: String) {
        notificationDao.markAllAsRead(userId, System.currentTimeMillis())
    }

    override suspend fun deleteOldNotifications(threshold: Long) {
        notificationDao.deleteOld(threshold)
    }

    override suspend fun clearAll() {
        notificationDao.clearAll()
    }
}

private fun NotificationEntity.toDomain(): Notification = Notification(
    notificationId = notificationId,
    userId = userId,
    title = title,
    body = body,
    type = NotificationType.valueOf(type),
    data = data,
    readAt = readAt,
    createdAt = createdAt,
)

private fun Notification.toEntity(): NotificationEntity = NotificationEntity(
    notificationId = notificationId,
    userId = userId,
    title = title,
    body = body,
    type = type.name,
    data = data,
    readAt = readAt,
    createdAt = createdAt,
)

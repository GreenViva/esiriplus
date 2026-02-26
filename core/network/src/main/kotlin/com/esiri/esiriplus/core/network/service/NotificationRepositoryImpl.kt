package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.database.dao.NotificationDao
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import com.esiri.esiriplus.core.domain.model.Notification
import com.esiri.esiriplus.core.domain.model.NotificationType
import com.esiri.esiriplus.core.domain.repository.NotificationRepository
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao,
    private val notificationSyncService: NotificationSyncService,
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
        // Best-effort sync read status to Supabase
        try {
            notificationSyncService.markAsReadRemote(notificationId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync read status to Supabase", e)
        }
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

    override suspend fun fetchAndStoreNotification(notificationId: String) {
        when (val result = notificationSyncService.fetchNotificationById(notificationId)) {
            is ApiResult.Success -> {
                val row = result.data ?: return
                val entity = row.toEntity()
                notificationDao.insert(entity)
                Log.d(TAG, "Fetched and stored notification $notificationId")
            }
            else -> Log.e(TAG, "Failed to fetch notification $notificationId: $result")
        }
    }

    override suspend fun syncFromRemote(userId: String) {
        when (val result = notificationSyncService.fetchRecentNotifications(userId)) {
            is ApiResult.Success -> {
                val entities = result.data.map { it.toEntity() }
                if (entities.isNotEmpty()) {
                    notificationDao.insertAll(entities)
                }
                Log.d(TAG, "Synced ${entities.size} notifications for $userId")
            }
            else -> Log.e(TAG, "Failed to sync notifications for $userId: $result")
        }
    }

    companion object {
        private const val TAG = "NotificationRepo"
    }
}

private fun NotificationEntity.toDomain(): Notification = Notification(
    notificationId = notificationId,
    userId = userId,
    title = title,
    body = body,
    type = NotificationType.fromString(type),
    data = data,
    readAt = readAt,
    createdAt = createdAt,
)

private fun NotificationRow.toEntity(): NotificationEntity {
    val readAtMillis = readAt?.let {
        try { Instant.parse(it).toEpochMilli() } catch (_: Exception) { null }
    }
    val createdAtMillis = try {
        Instant.parse(createdAt).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }

    return NotificationEntity(
        notificationId = notificationId,
        userId = userId,
        title = title,
        body = body,
        type = type.uppercase(),
        data = data,
        readAt = readAtMillis,
        createdAt = createdAtMillis,
    )
}

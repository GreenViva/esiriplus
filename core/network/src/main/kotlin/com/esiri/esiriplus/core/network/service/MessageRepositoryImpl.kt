package com.esiri.esiriplus.core.network.service

import com.esiri.esiriplus.core.database.dao.MessageDao
import com.esiri.esiriplus.core.database.entity.MessageEntity
import com.esiri.esiriplus.core.domain.repository.MessageData
import com.esiri.esiriplus.core.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
) : MessageRepository {

    override fun getByConsultationId(consultationId: String): Flow<List<MessageData>> {
        return messageDao.getByConsultationId(consultationId).map { entities ->
            entities.map { it.toData() }
        }
    }

    override suspend fun saveMessage(message: MessageData) {
        messageDao.insert(message.toEntity())
    }

    override suspend fun saveMessages(messages: List<MessageData>) {
        messageDao.insertAll(messages.map { it.toEntity() })
    }

    override suspend fun markAsRead(messageId: String) {
        messageDao.markAsRead(messageId)
    }

    override suspend fun markAsSynced(messageId: String) {
        messageDao.markAsSynced(messageId)
    }

    override suspend fun getUnsyncedMessages(): List<MessageData> {
        return messageDao.getUnsyncedMessages().map { it.toData() }
    }

    override suspend fun getLatestSyncedTimestamp(consultationId: String): Long? {
        return messageDao.getLatestSyncedTimestamp(consultationId)
    }

    override suspend fun clearAll() {
        messageDao.clearAll()
    }
}

private fun MessageEntity.toData(): MessageData = MessageData(
    messageId = messageId,
    consultationId = consultationId,
    senderType = senderType,
    senderId = senderId,
    messageText = messageText,
    messageType = messageType,
    attachmentUrl = attachmentUrl,
    isRead = isRead,
    synced = synced,
    createdAt = createdAt,
    retryCount = retryCount,
    failedAt = failedAt,
)

private fun MessageData.toEntity(): MessageEntity = MessageEntity(
    messageId = messageId,
    consultationId = consultationId,
    senderType = senderType,
    senderId = senderId,
    messageText = messageText,
    messageType = messageType,
    attachmentUrl = attachmentUrl,
    isRead = isRead,
    synced = synced,
    createdAt = createdAt,
    retryCount = retryCount,
    failedAt = failedAt,
)

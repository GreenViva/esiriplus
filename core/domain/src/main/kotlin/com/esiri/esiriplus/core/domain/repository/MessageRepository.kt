package com.esiri.esiriplus.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getByConsultationId(consultationId: String): Flow<List<MessageData>>
    suspend fun saveMessage(message: MessageData)
    suspend fun saveMessages(messages: List<MessageData>)
    suspend fun markAsRead(messageId: String)
    suspend fun markAsSynced(messageId: String)
    suspend fun getUnsyncedMessages(): List<MessageData>
    suspend fun clearAll()
}

data class MessageData(
    val messageId: String,
    val consultationId: String,
    val senderType: String,
    val senderId: String,
    val messageText: String,
    val messageType: String,
    val attachmentUrl: String? = null,
    val isRead: Boolean = false,
    val synced: Boolean = false,
    val createdAt: Long,
)

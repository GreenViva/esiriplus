package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["consultationId"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("consultationId", "createdAt"),
    ],
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
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

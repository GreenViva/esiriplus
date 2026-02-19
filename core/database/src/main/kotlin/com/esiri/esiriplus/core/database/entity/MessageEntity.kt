package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["id"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consultationId"), Index("senderId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val consultationId: String,
    val senderId: String,
    val content: String,
    val messageType: String,
    val isRead: Boolean = false,
    val createdAt: Instant,
)

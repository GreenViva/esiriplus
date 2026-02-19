package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "notifications",
    indices = [Index("userId")],
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val type: String,
    val referenceId: String? = null,
    val isRead: Boolean = false,
    val createdAt: Instant,
)

package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index("userId"),
        Index("type"),
    ],
)
data class NotificationEntity(
    @PrimaryKey val notificationId: String,
    val userId: String,
    val title: String,
    val body: String,
    val type: String,
    val data: String,
    val readAt: Long? = null,
    val createdAt: Long,
)

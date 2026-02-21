package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "typing_indicators",
    primaryKeys = ["consultationId", "userId"],
)
data class TypingIndicatorEntity(
    val consultationId: String,
    val userId: String,
    val isTyping: Boolean,
    val updatedAt: Long,
)

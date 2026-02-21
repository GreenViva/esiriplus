package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: Int = 0,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val userId: String,
    val createdAt: Instant = Instant.now(),
)

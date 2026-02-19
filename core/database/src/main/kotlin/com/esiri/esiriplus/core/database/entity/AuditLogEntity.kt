package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "audit_logs",
    indices = [Index("userId"), Index("action")],
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val details: String? = null,
    val ipAddress: String? = null,
    val createdAt: Instant,
)

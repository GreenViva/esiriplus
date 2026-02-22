package com.esiri.esiriplus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_tiers")
data class ServiceTierEntity(
    @PrimaryKey val id: String,
    val category: String,
    val displayName: String,
    val description: String,
    val priceAmount: Int,
    val currency: String,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "15") val durationMinutes: Int = 15,
    @ColumnInfo(defaultValue = "") val features: String = "",
)

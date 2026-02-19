package com.esiri.esiriplus.core.database.entity

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
)

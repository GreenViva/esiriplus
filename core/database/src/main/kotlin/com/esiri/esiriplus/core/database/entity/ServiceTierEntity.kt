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
    /** Economy-tier price for this service. */
    val priceAmount: Int,
    /**
     * Royal-tier price for this service. Set explicitly per service (since
     * 2026-05-01) — no longer computed as priceAmount × 10.
     */
    @ColumnInfo(defaultValue = "0") val royalPrice: Int = 0,
    val currency: String,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "15") val durationMinutes: Int = 15,
    @ColumnInfo(defaultValue = "") val features: String = "",
)

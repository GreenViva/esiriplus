package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val licenseNumber: String? = null,
    val isActive: Boolean = true,
)

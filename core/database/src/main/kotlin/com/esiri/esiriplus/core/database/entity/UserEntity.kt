package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val phone: String,
    val email: String?,
    val role: String,
    val isVerified: Boolean,
)

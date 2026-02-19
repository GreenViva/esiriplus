package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctor_profiles",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId", unique = true)],
)
data class DoctorProfileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val specialization: String,
    val licenseNumber: String,
    val bio: String? = null,
    val yearsOfExperience: Int = 0,
    val isAvailable: Boolean = true,
)

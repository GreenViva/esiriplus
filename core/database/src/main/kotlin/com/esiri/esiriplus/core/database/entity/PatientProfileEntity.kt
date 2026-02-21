package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "patient_profiles",
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
data class PatientProfileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateOfBirth: Instant? = null,
    val bloodGroup: String? = null,
    val allergies: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val sex: String? = null,
    val ageGroup: String? = null,
    val chronicConditions: String? = null,
)

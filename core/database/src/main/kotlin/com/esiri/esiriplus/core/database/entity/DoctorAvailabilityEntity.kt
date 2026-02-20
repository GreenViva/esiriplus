package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctor_availability",
    foreignKeys = [
        ForeignKey(
            entity = DoctorProfileEntity::class,
            parentColumns = ["doctorId"],
            childColumns = ["doctorId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("doctorId")],
)
data class DoctorAvailabilityEntity(
    @PrimaryKey val availabilityId: String,
    val doctorId: String,
    val isAvailable: Boolean,
    val availabilitySchedule: String,
    val lastUpdated: Long,
)

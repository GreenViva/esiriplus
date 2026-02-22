package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctor_availability",
    indices = [Index("doctorId")],
)
data class DoctorAvailabilityEntity(
    @PrimaryKey val availabilityId: String,
    val doctorId: String,
    val isAvailable: Boolean,
    val availabilitySchedule: String,
    val lastUpdated: Long,
)

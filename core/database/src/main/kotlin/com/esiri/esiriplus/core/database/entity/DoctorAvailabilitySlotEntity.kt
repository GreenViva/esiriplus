package com.esiri.esiriplus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctor_availability_slots",
    indices = [
        Index("doctorId", "isActive"),
        Index("dayOfWeek", "isActive"),
    ],
)
data class DoctorAvailabilitySlotEntity(
    @PrimaryKey val slotId: String,
    val doctorId: String,
    val dayOfWeek: Int, // 0=Sunday, 6=Saturday
    val startTime: String, // HH:mm format
    val endTime: String, // HH:mm format
    @ColumnInfo(defaultValue = "5") val bufferMinutes: Int = 5,
    @ColumnInfo(defaultValue = "1") val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

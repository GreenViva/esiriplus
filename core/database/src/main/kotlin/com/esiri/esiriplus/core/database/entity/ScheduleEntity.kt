package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "schedules",
    indices = [Index("doctorId")],
)
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val doctorId: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val isAvailable: Boolean = true,
    val effectiveFrom: Instant,
    val effectiveTo: Instant? = null,
)

package com.esiri.esiriplus.core.domain.model

data class DoctorAvailabilitySlot(
    val slotId: String,
    val doctorId: String,
    val dayOfWeek: Int, // 0=Sunday, 6=Saturday
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val bufferMinutes: Int = 5,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

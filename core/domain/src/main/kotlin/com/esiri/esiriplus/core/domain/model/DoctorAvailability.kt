package com.esiri.esiriplus.core.domain.model

data class DoctorAvailability(
    val availabilityId: String,
    val doctorId: String,
    val isAvailable: Boolean,
    val availabilitySchedule: String,
    val lastUpdated: Long,
)

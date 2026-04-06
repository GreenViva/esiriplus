package com.esiri.esiriplus.feature.doctor.viewmodel

import kotlinx.serialization.Serializable

// ─── Day schedule model ─────────────────────────────────────────────────────────

@Serializable
data class DaySchedule(
    val enabled: Boolean = false,
    val start: String = "09:00",
    val end: String = "17:00",
)

data class WeeklySchedule(
    val sunday: DaySchedule = DaySchedule(),
    val monday: DaySchedule = DaySchedule(enabled = true),
    val tuesday: DaySchedule = DaySchedule(enabled = true),
    val wednesday: DaySchedule = DaySchedule(enabled = true),
    val thursday: DaySchedule = DaySchedule(enabled = true),
    val friday: DaySchedule = DaySchedule(enabled = true),
    val saturday: DaySchedule = DaySchedule(),
) {
    fun toMap(): Map<String, DaySchedule> = mapOf(
        "Sunday" to sunday,
        "Monday" to monday,
        "Tuesday" to tuesday,
        "Wednesday" to wednesday,
        "Thursday" to thursday,
        "Friday" to friday,
        "Saturday" to saturday,
    )

    fun withDay(dayName: String, schedule: DaySchedule): WeeklySchedule = when (dayName) {
        "Sunday" -> copy(sunday = schedule)
        "Monday" -> copy(monday = schedule)
        "Tuesday" -> copy(tuesday = schedule)
        "Wednesday" -> copy(wednesday = schedule)
        "Thursday" -> copy(thursday = schedule)
        "Friday" -> copy(friday = schedule)
        "Saturday" -> copy(saturday = schedule)
        else -> this
    }
}

// ─── Earnings transaction model ─────────────────────────────────────────────────

data class EarningsTransaction(
    val id: String,
    val patientName: String,
    val amount: String,
    val date: String,
    val status: String,
    val earningType: String = "consultation",
)

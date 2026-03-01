package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Appointment
import com.esiri.esiriplus.core.domain.model.DoctorAvailabilitySlot

interface AppointmentRepository {

    suspend fun bookAppointment(
        doctorId: String,
        scheduledAt: String,
        durationMinutes: Int,
        serviceType: String,
        consultationType: String,
        chiefComplaint: String,
    ): Result<Appointment>

    suspend fun cancelAppointment(appointmentId: String): Result<Appointment>

    suspend fun rescheduleAppointment(
        appointmentId: String,
        newScheduledAt: String,
        reason: String,
    ): Result<Appointment>

    suspend fun getAvailableSlots(
        doctorId: String,
        date: String,
    ): Result<AvailableSlotsResponse>

    suspend fun getAppointments(
        status: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<List<Appointment>>

    suspend fun syncAppointments(): Result<Unit>
}

data class AvailableSlotsResponse(
    val doctorId: String,
    val date: String,
    val dayOfWeek: Int,
    val availabilitySlots: List<DoctorAvailabilitySlot>,
    val bookedAppointments: List<BookedSlot>,
    val inSession: Boolean,
    val maxAppointmentsPerDay: Int,
)

data class BookedSlot(
    val appointmentId: String,
    val scheduledAt: String,
    val durationMinutes: Int,
    val status: String,
)

package com.esiri.esiriplus.core.network.service

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Appointment
import com.esiri.esiriplus.core.domain.model.AppointmentStatus
import com.esiri.esiriplus.core.domain.model.DoctorAvailabilitySlot
import com.esiri.esiriplus.core.domain.repository.AppointmentRepository
import com.esiri.esiriplus.core.domain.repository.AvailableSlotsResponse
import com.esiri.esiriplus.core.domain.repository.BookedSlot
import com.esiri.esiriplus.core.network.model.toDomainResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppointmentRepositoryImpl @Inject constructor(
    private val appointmentService: AppointmentService,
    private val rescheduleService: RescheduleService,
) : AppointmentRepository {

    override suspend fun bookAppointment(
        doctorId: String,
        scheduledAt: String,
        durationMinutes: Int,
        serviceType: String,
        consultationType: String,
        chiefComplaint: String,
    ): Result<Appointment> {
        return appointmentService.bookAppointment(
            doctorId = doctorId,
            scheduledAt = scheduledAt,
            durationMinutes = durationMinutes,
            serviceType = serviceType,
            consultationType = consultationType,
            chiefComplaint = chiefComplaint,
        ).map { it.toDomain() }.toDomainResult()
    }

    override suspend fun cancelAppointment(appointmentId: String): Result<Appointment> {
        return appointmentService.cancelAppointment(appointmentId)
            .map { it.toDomain() }
            .toDomainResult()
    }

    override suspend fun rescheduleAppointment(
        appointmentId: String,
        newScheduledAt: String,
        reason: String,
    ): Result<Appointment> {
        return rescheduleService.rescheduleAppointment(appointmentId, newScheduledAt, reason)
            .map { row ->
                Appointment(
                    appointmentId = row.newAppointmentId,
                    doctorId = row.doctorId,
                    patientSessionId = "",
                    scheduledAt = parseTimestamp(row.scheduledAt),
                    status = AppointmentStatus.fromString(row.status),
                    serviceType = "",
                    createdAt = parseTimestamp(row.createdAt),
                    updatedAt = parseTimestamp(row.createdAt),
                )
            }
            .toDomainResult()
    }

    override suspend fun getAvailableSlots(
        doctorId: String,
        date: String,
    ): Result<AvailableSlotsResponse> {
        return appointmentService.getAvailableSlots(doctorId, date)
            .map { response ->
                AvailableSlotsResponse(
                    doctorId = response.doctorId,
                    date = response.date,
                    dayOfWeek = response.dayOfWeek,
                    availabilitySlots = response.availabilitySlots.map { slot ->
                        DoctorAvailabilitySlot(
                            slotId = slot.slotId,
                            doctorId = doctorId,
                            dayOfWeek = response.dayOfWeek,
                            startTime = slot.startTime,
                            endTime = slot.endTime,
                            bufferMinutes = slot.bufferMinutes,
                            isActive = true,
                            createdAt = 0,
                            updatedAt = 0,
                        )
                    },
                    bookedAppointments = response.bookedAppointments.map { apt ->
                        BookedSlot(
                            appointmentId = apt.appointmentId,
                            scheduledAt = apt.scheduledAt,
                            durationMinutes = apt.durationMinutes,
                            status = apt.status,
                        )
                    },
                    inSession = response.inSession,
                    maxAppointmentsPerDay = response.maxAppointmentsPerDay,
                )
            }
            .toDomainResult()
    }

    override suspend fun getAppointments(
        status: String?,
        limit: Int,
        offset: Int,
    ): Result<List<Appointment>> {
        return appointmentService.getAppointments(status, limit, offset)
            .map { response ->
                response.appointments.map { it.toDomain() }
            }
            .toDomainResult()
    }

    override suspend fun syncAppointments(): Result<Unit> {
        return appointmentService.getAppointments(limit = 100)
            .map { /* Sync to local DB could be done here */ }
            .toDomainResult()
    }
}

private fun AppointmentRow.toDomain(): Appointment {
    return Appointment(
        appointmentId = appointmentId,
        doctorId = doctorId ?: "",
        patientSessionId = "",
        scheduledAt = parseTimestamp(scheduledAt),
        status = AppointmentStatus.fromString(status),
        serviceType = "",
        createdAt = parseTimestamp(createdAt),
        updatedAt = parseTimestamp(createdAt),
    )
}

private fun AppointmentFullRow.toDomain(): Appointment {
    return Appointment(
        appointmentId = appointmentId,
        doctorId = doctorId,
        patientSessionId = patientSessionId,
        scheduledAt = parseTimestamp(scheduledAt),
        durationMinutes = durationMinutes,
        status = AppointmentStatus.fromString(status),
        serviceType = serviceType,
        consultationType = consultationType,
        chiefComplaint = chiefComplaint,
        consultationFee = consultationFee,
        consultationId = consultationId,
        rescheduledFrom = rescheduledFrom,
        createdAt = parseTimestamp(createdAt),
        updatedAt = parseTimestamp(updatedAt),
    )
}

private fun parseTimestamp(value: String?): Long {
    if (value == null) return System.currentTimeMillis()
    return try {
        java.time.Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

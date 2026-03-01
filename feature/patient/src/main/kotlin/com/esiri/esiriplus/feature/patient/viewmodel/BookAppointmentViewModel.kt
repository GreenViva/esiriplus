package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.domain.repository.AppointmentRepository
import com.esiri.esiriplus.core.domain.repository.AvailableSlotsResponse
import com.esiri.esiriplus.core.domain.repository.BookedSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@Serializable
data class DayScheduleDto(
    val enabled: Boolean = false,
    val start: String = "",
    val end: String = "",
)

data class TimeSlot(
    val time: LocalTime,
    val label: String,
    val isAvailable: Boolean,
)

data class BookAppointmentUiState(
    val doctorName: String = "",
    val specialty: String = "",
    val averageRating: Double = 0.0,
    val totalRatings: Int = 0,
    val isVerified: Boolean = false,
    val isLoadingDoctor: Boolean = true,
    val isLoadingSlots: Boolean = false,

    // Date selection
    val selectedDate: LocalDate? = null,
    val availableDates: List<LocalDate> = emptyList(), // next 14 days

    // Time slots for selected date
    val timeSlots: List<TimeSlot> = emptyList(),
    val selectedTime: LocalTime? = null,

    // Booking form
    val chiefComplaint: String = "",
    val durationMinutes: Int = 15,
    val isSubmitting: Boolean = false,
    val bookingSuccess: String? = null, // appointment ID on success
    val errorMessage: String? = null,

    // Slot info
    val inSession: Boolean = false,
    val maxAppointmentsPerDay: Int = 10,
)

private val EAT = ZoneId.of("Africa/Nairobi")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@HiltViewModel
class BookAppointmentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appointmentRepository: AppointmentRepository,
    private val doctorProfileDao: DoctorProfileDao,
) : ViewModel() {

    private val doctorId: String = savedStateHandle["doctorId"] ?: ""
    private val serviceCategory: String = savedStateHandle["serviceCategory"] ?: ""
    private val servicePriceAmount: Int = savedStateHandle["servicePriceAmount"] ?: 0
    private val serviceDurationMinutes: Int = savedStateHandle["serviceDurationMinutes"] ?: 15

    private val _uiState = MutableStateFlow(
        BookAppointmentUiState(durationMinutes = serviceDurationMinutes),
    )
    val uiState: StateFlow<BookAppointmentUiState> = _uiState.asStateFlow()

    // Cached slot data per date
    private var cachedSlotsResponse: AvailableSlotsResponse? = null

    init {
        loadDoctorInfo()
        initDates()
    }

    private fun loadDoctorInfo() {
        viewModelScope.launch {
            val doctor = doctorProfileDao.getById(doctorId)
            if (doctor != null) {
                _uiState.update {
                    it.copy(
                        doctorName = doctor.fullName,
                        specialty = specialtyDisplayNames[doctor.specialty] ?: doctor.specialty,
                        averageRating = doctor.averageRating,
                        totalRatings = doctor.totalRatings,
                        isVerified = doctor.isVerified,
                        isLoadingDoctor = false,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingDoctor = false) }
            }
        }
    }

    private fun initDates() {
        val today = LocalDate.now(EAT)
        val dates = (0 until 14).map { today.plusDays(it.toLong()) }
        _uiState.update { it.copy(availableDates = dates) }
        selectDate(today)
    }

    fun selectDate(date: LocalDate) {
        _uiState.update {
            it.copy(
                selectedDate = date,
                selectedTime = null,
                timeSlots = emptyList(),
                isLoadingSlots = true,
            )
        }
        loadSlotsForDate(date)
    }

    private fun loadSlotsForDate(date: LocalDate) {
        viewModelScope.launch {
            val dateStr = date.toString() // YYYY-MM-DD
            when (val result = appointmentRepository.getAvailableSlots(doctorId, dateStr)) {
                is Result.Success -> {
                    Log.d(TAG, "getAvailableSlots OK: date=$dateStr, availabilitySlots=${result.data.availabilitySlots.size}, bookedAppointments=${result.data.bookedAppointments.size}, dayOfWeek=${result.data.dayOfWeek}")
                    cachedSlotsResponse = result.data
                    val slots = generateTimeSlots(result.data, date)
                    _uiState.update {
                        it.copy(
                            timeSlots = slots,
                            isLoadingSlots = false,
                            inSession = result.data.inSession,
                            maxAppointmentsPerDay = result.data.maxAppointmentsPerDay,
                        )
                    }
                }
                is Result.Error -> {
                    Log.w(TAG, "Failed to load slots: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isLoadingSlots = false,
                            errorMessage = "Failed to load available times",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun generateTimeSlots(
        response: AvailableSlotsResponse,
        date: LocalDate,
    ): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        val now = ZonedDateTime.now(EAT)
        val isToday = date == now.toLocalDate()

        for (availSlot in response.availabilitySlots) {
            val startTime = LocalTime.parse(availSlot.startTime)
            val endTime = LocalTime.parse(availSlot.endTime)
            val intervalMinutes = serviceDurationMinutes.toLong()

            var current = startTime
            while (current.plusMinutes(intervalMinutes) <= endTime) {
                // Skip past times if today
                val isInPast = isToday && current <= now.toLocalTime()

                // Check if this time overlaps with any booked appointment
                val isBooked = isTimeBooked(date, current, intervalMinutes.toInt(), response.bookedAppointments)

                slots.add(
                    TimeSlot(
                        time = current,
                        label = current.format(timeFormatter),
                        isAvailable = !isInPast && !isBooked,
                    ),
                )
                current = current.plusMinutes(intervalMinutes + availSlot.bufferMinutes.toLong())
            }
        }
        return slots
    }

    private fun isTimeBooked(
        date: LocalDate,
        time: LocalTime,
        durationMinutes: Int,
        bookedAppointments: List<BookedSlot>,
    ): Boolean {
        val slotStart = ZonedDateTime.of(date, time, EAT).toInstant()
        val slotEnd = slotStart.plusSeconds(durationMinutes * 60L)

        return bookedAppointments.any { booked ->
            val bookedStart = Instant.parse(booked.scheduledAt)
            val bookedEnd = bookedStart.plusSeconds(booked.durationMinutes * 60L)
            // Check overlap
            slotStart < bookedEnd && slotEnd > bookedStart
        }
    }

    fun selectTime(time: LocalTime) {
        _uiState.update { it.copy(selectedTime = time, errorMessage = null) }
    }

    fun updateChiefComplaint(value: String) {
        _uiState.update { it.copy(chiefComplaint = value, errorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun bookAppointment() {
        val state = _uiState.value
        if (state.selectedDate == null || state.selectedTime == null) {
            _uiState.update { it.copy(errorMessage = "Please select a date and time") }
            return
        }
        if (state.chiefComplaint.trim().length < 10) {
            _uiState.update { it.copy(errorMessage = "Please describe your complaint (at least 10 characters)") }
            return
        }
        if (state.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        val scheduledAt = ZonedDateTime.of(state.selectedDate, state.selectedTime, EAT)
            .toInstant()
            .toString() // ISO-8601

        viewModelScope.launch {
            val result = appointmentRepository.bookAppointment(
                doctorId = doctorId,
                scheduledAt = scheduledAt,
                durationMinutes = serviceDurationMinutes,
                serviceType = serviceCategory.lowercase(),
                consultationType = "chat",
                chiefComplaint = state.chiefComplaint.trim(),
            )

            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            bookingSuccess = result.data.appointmentId,
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message ?: result.exception.message
                                ?: "Failed to book appointment",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    companion object {
        private const val TAG = "BookAppointmentVM"
    }
}

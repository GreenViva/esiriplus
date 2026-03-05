package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.domain.repository.AppointmentRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.domain.repository.AvailableSlotsResponse
import com.esiri.esiriplus.core.domain.repository.BookedSlot
import com.esiri.esiriplus.feature.patient.R
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

// TODO: Localize hardcoded user-facing strings (error messages).
//  Inject Application context and use context.getString(R.string.xxx) from feature.patient.R
@HiltViewModel
class BookAppointmentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appointmentRepository: AppointmentRepository,
    private val doctorProfileDao: DoctorProfileDao,
    private val edgeFunctionClient: EdgeFunctionClient,
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
            var doctor = doctorProfileDao.getById(doctorId)
            if (doctor == null) {
                // Fallback: fetch from backend
                doctor = fetchDoctorFromBackend(doctorId)
            }
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

    private suspend fun fetchDoctorFromBackend(id: String): DoctorProfileEntity? {
        return try {
            val body = kotlinx.serialization.json.buildJsonObject {
                put("specialty", kotlinx.serialization.json.JsonPrimitive(serviceCategory.lowercase()))
            }
            when (val result = edgeFunctionClient.invoke("list-doctors", body)) {
                is ApiResult.Success -> {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    val response = json.decodeFromString<ListDoctorsForBooking>(result.data)
                    val match = response.doctors.firstOrNull { it.doctor_id == id }
                    match?.let {
                        val now = System.currentTimeMillis()
                        val entity = DoctorProfileEntity(
                            doctorId = it.doctor_id,
                            fullName = it.full_name,
                            email = it.email ?: "",
                            phone = it.phone ?: "",
                            specialty = it.specialty ?: "",
                            languages = emptyList(),
                            bio = it.bio ?: "",
                            licenseNumber = "",
                            yearsExperience = 0,
                            profilePhotoUrl = it.profile_photo_url,
                            averageRating = it.average_rating ?: 0.0,
                            totalRatings = it.total_ratings ?: 0,
                            isVerified = it.is_verified ?: false,
                            isAvailable = it.is_available ?: false,
                            services = emptyList(),
                            countryCode = "+255",
                            country = "",
                            createdAt = now,
                            updatedAt = now,
                        )
                        doctorProfileDao.insert(entity)
                        entity
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch doctor from backend", e)
            null
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

@kotlinx.serialization.Serializable
private data class ListDoctorsForBooking(val doctors: List<DoctorRowForBooking> = emptyList())

@kotlinx.serialization.Serializable
private data class DoctorRowForBooking(
    val doctor_id: String,
    val full_name: String,
    val email: String? = null,
    val phone: String? = null,
    val specialty: String? = null,
    val bio: String? = null,
    val profile_photo_url: String? = null,
    val average_rating: Double? = null,
    val total_ratings: Int? = null,
    val is_verified: Boolean? = null,
    val is_available: Boolean? = null,
)

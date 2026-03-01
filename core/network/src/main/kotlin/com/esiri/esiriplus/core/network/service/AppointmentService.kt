package com.esiri.esiriplus.core.network.service

import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AppointmentRow(
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("doctor_id") val doctorId: String? = null,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AvailabilitySlotsRow(
    @SerialName("slot_id") val slotId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("buffer_minutes") val bufferMinutes: Int = 5,
)

@Serializable
data class BookedAppointmentRow(
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val status: String,
)

@Serializable
data class GetSlotsResponse(
    @SerialName("doctor_id") val doctorId: String,
    val date: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("availability_slots") val availabilitySlots: List<AvailabilitySlotsRow>,
    @SerialName("booked_appointments") val bookedAppointments: List<BookedAppointmentRow>,
    @SerialName("in_session") val inSession: Boolean = false,
    @SerialName("max_appointments_per_day") val maxAppointmentsPerDay: Int = 10,
)

@Serializable
data class GetAppointmentsResponse(
    val appointments: List<AppointmentFullRow>,
    val count: Int,
)

@Serializable
data class AppointmentFullRow(
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("patient_session_id") val patientSessionId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int = 15,
    val status: String,
    @SerialName("service_type") val serviceType: String,
    @SerialName("consultation_type") val consultationType: String = "chat",
    @SerialName("chief_complaint") val chiefComplaint: String = "",
    @SerialName("consultation_fee") val consultationFee: Int = 0,
    @SerialName("consultation_id") val consultationId: String? = null,
    @SerialName("rescheduled_from") val rescheduledFrom: String? = null,
    @SerialName("reminders_sent") val remindersSent: List<String> = emptyList(),
    @SerialName("grace_period_minutes") val gracePeriodMinutes: Int = 5,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Singleton
class AppointmentService @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) {

    suspend fun bookAppointment(
        doctorId: String,
        scheduledAt: String,
        durationMinutes: Int,
        serviceType: String,
        consultationType: String,
        chiefComplaint: String,
    ): ApiResult<AppointmentRow> {
        val body = buildJsonObject {
            put("action", "book")
            put("doctor_id", doctorId)
            put("scheduled_at", scheduledAt)
            put("duration_minutes", durationMinutes)
            put("service_type", serviceType)
            put("consultation_type", consultationType)
            put("chief_complaint", chiefComplaint)
        }
        return decodeResult(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun cancelAppointment(appointmentId: String): ApiResult<AppointmentRow> {
        val body = buildJsonObject {
            put("action", "cancel")
            put("appointment_id", appointmentId)
        }
        return decodeResult(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun getAvailableSlots(
        doctorId: String,
        date: String,
    ): ApiResult<GetSlotsResponse> {
        val body = buildJsonObject {
            put("action", "get_slots")
            put("doctor_id", doctorId)
            put("date", date)
        }
        return decodeResponse(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun getAppointments(
        status: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): ApiResult<GetAppointmentsResponse> {
        val body = buildJsonObject {
            put("action", "get_appointments")
            if (status != null) put("status", status)
            put("limit", limit)
            put("offset", offset)
        }
        return decodeResponse(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    private inline fun <reified T> decodeResponse(rawResult: ApiResult<String>): ApiResult<T> {
        return when (rawResult) {
            is ApiResult.Success -> try {
                ApiResult.Success(edgeFunctionClient.json.decodeFromString<T>(rawResult.data))
            } catch (e: Exception) {
                ApiResult.NetworkError(e, "Failed to parse response: ${e.message}")
            }
            is ApiResult.Error -> rawResult
            is ApiResult.NetworkError -> rawResult
            is ApiResult.Unauthorized -> rawResult
        }
    }

    private fun decodeResult(rawResult: ApiResult<String>): ApiResult<AppointmentRow> {
        return when (rawResult) {
            is ApiResult.Success -> try {
                ApiResult.Success(edgeFunctionClient.json.decodeFromString<AppointmentRow>(rawResult.data))
            } catch (e: Exception) {
                ApiResult.NetworkError(e, "Failed to parse response: ${e.message}")
            }
            is ApiResult.Error -> rawResult
            is ApiResult.NetworkError -> rawResult
            is ApiResult.Unauthorized -> rawResult
        }
    }

    companion object {
        private const val FUNCTION_NAME = "book-appointment"
    }
}

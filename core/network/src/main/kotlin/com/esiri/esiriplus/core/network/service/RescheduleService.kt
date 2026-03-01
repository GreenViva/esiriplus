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
data class RescheduleRow(
    @SerialName("new_appointment_id") val newAppointmentId: String,
    @SerialName("old_appointment_id") val oldAppointmentId: String,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Singleton
class RescheduleService @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) {

    suspend fun rescheduleAppointment(
        appointmentId: String,
        newScheduledAt: String,
        reason: String,
    ): ApiResult<RescheduleRow> {
        val body = buildJsonObject {
            put("appointment_id", appointmentId)
            put("new_scheduled_at", newScheduledAt)
            put("reason", reason)
        }
        return when (val rawResult = edgeFunctionClient.invoke(FUNCTION_NAME, body)) {
            is ApiResult.Success -> try {
                ApiResult.Success(edgeFunctionClient.json.decodeFromString<RescheduleRow>(rawResult.data))
            } catch (e: Exception) {
                ApiResult.NetworkError(e, "Failed to parse response: ${e.message}")
            }
            is ApiResult.Error -> rawResult
            is ApiResult.NetworkError -> rawResult
            is ApiResult.Unauthorized -> rawResult
        }
    }

    companion object {
        private const val FUNCTION_NAME = "reschedule-appointment"
    }
}

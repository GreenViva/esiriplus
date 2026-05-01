package com.esiri.esiriplus.core.network.service

import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network calls for the nurse-side medication reminder flow.
 *
 * Server-side counterpart: `supabase/functions/medication-reminder-callback`.
 * The flow is two-stage: cron rings the nurse with a 60s window
 * (`accept_ring` / `decline_ring`); on accept, the reminder lands on the
 * nurse's Medical Reminder list; tapping Call invokes `start_call` which
 * pushes the patient and returns the VideoSDK room id; the nurse marks the
 * call `completed` (earns 2,000 TZS) or `patient_unreachable`.
 */
@Serializable
data class AcceptedReminder(
    @SerialName("event_id") val eventId: String,
    /** Server-side status: nurse_ringing | nurse_notified | nurse_accepted | nurse_calling */
    val status: String = "",
    @SerialName("scheduled_date") val scheduledDate: String,
    @SerialName("scheduled_time") val scheduledTime: String,
    @SerialName("video_room_id") val videoRoomId: String? = null,
    @SerialName("ring_expires_at") val ringExpiresAt: String? = null,
    @SerialName("nurse_accepted_at") val nurseAcceptedAt: String? = null,
    @SerialName("medication_timetables") val timetable: TimetableInfo? = null,
)

@Serializable
data class TimetableInfo(
    @SerialName("patient_session_id") val patientSessionId: String,
    @SerialName("medication_name") val medicationName: String,
    val dosage: String? = null,
    val form: String? = null,
)

@Serializable
data class StartCallResult(
    val ok: Boolean,
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("patient_session_id") val patientSessionId: String? = null,
    @SerialName("consultation_id") val consultationId: String? = null,
)

@Serializable
private data class AcceptedListResponse(
    val reminders: List<AcceptedReminder> = emptyList(),
)

@Singleton
class MedicationReminderService @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun acceptRing(eventId: String): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "accept_ring")
            put("event_id", eventId)
        }
        return mapOk(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun declineRing(eventId: String): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "decline_ring")
            put("event_id", eventId)
        }
        return mapOk(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun startCall(eventId: String): ApiResult<StartCallResult> {
        val body = buildJsonObject {
            put("action", "start_call")
            put("event_id", eventId)
        }
        return when (val r = edgeFunctionClient.invoke(FUNCTION_NAME, body)) {
            is ApiResult.Success -> runCatching {
                ApiResult.Success(json.decodeFromString<StartCallResult>(r.data))
            }.getOrElse { ApiResult.NetworkError(it as Exception, "parse: ${it.message}") }
            is ApiResult.Error -> r
            is ApiResult.NetworkError -> r
            is ApiResult.Unauthorized -> r
        }
    }

    suspend fun listAcceptedReminders(): ApiResult<List<AcceptedReminder>> {
        val body = buildJsonObject { put("action", "list_accepted_reminders") }
        return when (val r = edgeFunctionClient.invoke(FUNCTION_NAME, body)) {
            is ApiResult.Success -> runCatching {
                ApiResult.Success(json.decodeFromString<AcceptedListResponse>(r.data).reminders)
            }.getOrElse { ApiResult.NetworkError(it as Exception, "parse: ${it.message}") }
            is ApiResult.Error -> r
            is ApiResult.NetworkError -> r
            is ApiResult.Unauthorized -> r
        }
    }

    suspend fun markCompleted(eventId: String): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "completed")
            put("event_id", eventId)
        }
        return mapOk(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun markPatientUnreachable(eventId: String): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "patient_unreachable")
            put("event_id", eventId)
        }
        return mapOk(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    private fun mapOk(r: ApiResult<String>): ApiResult<Unit> = when (r) {
        is ApiResult.Success -> ApiResult.Success(Unit)
        is ApiResult.Error -> r
        is ApiResult.NetworkError -> r
        is ApiResult.Unauthorized -> r
    }

    companion object {
        private const val FUNCTION_NAME = "medication-reminder-callback"
    }
}

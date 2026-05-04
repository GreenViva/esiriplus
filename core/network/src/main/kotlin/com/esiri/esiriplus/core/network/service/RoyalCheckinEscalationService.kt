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
 * Network calls for the Royal check-in CO escalation flow.
 *
 * Server-side counterpart: `supabase/functions/royal-checkin-escalation-callback`.
 * The cron rings a clinical officer when a doctor's 3 attempts at a slot go
 * unacknowledged; the CO accepts the ring, calls each Royal patient, then
 * marks the escalation complete. Per-call earnings of 2,000 TZS are credited
 * server-side for any call where the patient picked up AND duration > 60s.
 */
@Serializable
data class RoyalEscalationDoctor(
    @SerialName("full_name") val fullName: String? = null,
    val specialty: String? = null,
)

@Serializable
data class RoyalEscalationCall(
    @SerialName("call_id") val callId: String,
    @SerialName("patient_session_id") val patientSessionId: String,
    @SerialName("call_started_at") val callStartedAt: String? = null,
    @SerialName("call_ended_at") val callEndedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("patient_accepted") val patientAccepted: Boolean = false,
)

@Serializable
data class RoyalEscalationItem(
    @SerialName("escalation_id") val escalationId: String,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("slot_date") val slotDate: String,
    @SerialName("slot_hour") val slotHour: Int,
    val status: String,
    @SerialName("ring_expires_at") val ringExpiresAt: String? = null,
    @SerialName("co_accepted_at") val coAcceptedAt: String? = null,
    @SerialName("patient_session_id") val patientSessionId: String,
    @SerialName("consultation_id") val consultationId: String? = null,
    val doctor: RoyalEscalationDoctor? = null,
    val calls: List<RoyalEscalationCall> = emptyList(),
)

@Serializable
private data class EscalationListResponse(
    val escalations: List<RoyalEscalationItem> = emptyList(),
)

@Serializable
data class RoyalStartCallResult(
    val ok: Boolean,
    @SerialName("call_id") val callId: String? = null,
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("consultation_id") val consultationId: String? = null,
    @SerialName("patient_session_id") val patientSessionId: String? = null,
)

@Singleton
class RoyalCheckinEscalationService @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listActiveEscalations(): ApiResult<List<RoyalEscalationItem>> {
        val body = buildJsonObject { put("action", "list_active_escalations") }
        return when (val r = edgeFunctionClient.invoke(FUNCTION_NAME, body)) {
            is ApiResult.Success -> runCatching {
                ApiResult.Success(json.decodeFromString<EscalationListResponse>(r.data).escalations)
            }.getOrElse { ApiResult.NetworkError(it as Exception, "parse: ${it.message}") }
            is ApiResult.Error -> r
            is ApiResult.NetworkError -> r
            is ApiResult.Unauthorized -> r
        }
    }

    suspend fun acceptRing(escalationId: String): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "accept_ring")
            put("escalation_id", escalationId)
        }
        return mapOk(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun declineRing(escalationId: String): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "decline_ring")
            put("escalation_id", escalationId)
        }
        return mapOk(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun startCall(escalationId: String): ApiResult<RoyalStartCallResult> {
        val body = buildJsonObject {
            put("action", "start_call")
            put("escalation_id", escalationId)
        }
        return when (val r = edgeFunctionClient.invoke(FUNCTION_NAME, body)) {
            is ApiResult.Success -> runCatching {
                ApiResult.Success(json.decodeFromString<RoyalStartCallResult>(r.data))
            }.getOrElse { ApiResult.NetworkError(it as Exception, "parse: ${it.message}") }
            is ApiResult.Error -> r
            is ApiResult.NetworkError -> r
            is ApiResult.Unauthorized -> r
        }
    }

    suspend fun endCall(callId: String, patientAccepted: Boolean, durationSeconds: Int): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "end_call")
            put("call_id", callId)
            put("patient_accepted", patientAccepted)
            put("duration_seconds", durationSeconds)
        }
        return mapOk(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun completeEscalation(escalationId: String): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "complete_escalation")
            put("escalation_id", escalationId)
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
        private const val FUNCTION_NAME = "royal-checkin-escalation-callback"
    }
}

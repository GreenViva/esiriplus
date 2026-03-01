package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Realtime event data emitted when a consultation_request row changes.
 */
data class RequestRealtimeEvent(
    val requestId: String,
    val status: String,
    val consultationId: String? = null,
    val patientSessionId: String? = null,
    val serviceType: String? = null,
)

/**
 * Subscribes to Supabase Realtime changes on the `consultation_requests` table.
 *
 * - Patient subscribes filtered by `patient_session_id`
 * - Doctor subscribes filtered by `doctor_id`
 *
 * Emits [RequestRealtimeEvent] on every INSERT or UPDATE.
 */
@Singleton
class ConsultationRequestRealtimeService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {

    private val _requestEvents = MutableSharedFlow<RequestRealtimeEvent>(extraBufferCapacity = 16)
    val requestEvents: SharedFlow<RequestRealtimeEvent> = _requestEvents.asSharedFlow()

    private var channel: RealtimeChannel? = null

    /**
     * Subscribe as a patient — listens for status changes on requests created by this session.
     */
    suspend fun subscribeAsPatient(patientSessionId: String, scope: CoroutineScope) {
        subscribe(
            channelName = "patient-requests-$patientSessionId",
            filterColumn = "patient_session_id",
            filterValue = patientSessionId,
            scope = scope,
        )
    }

    /**
     * Subscribe as a doctor — listens for new incoming requests and status updates.
     */
    suspend fun subscribeAsDoctor(doctorId: String, scope: CoroutineScope) {
        subscribe(
            channelName = "doctor-requests-$doctorId",
            filterColumn = "doctor_id",
            filterValue = doctorId,
            scope = scope,
        )
    }

    private suspend fun subscribe(
        channelName: String,
        filterColumn: String,
        filterValue: String,
        scope: CoroutineScope,
    ) {
        try {
            unsubscribe()

            val ch = supabaseClientProvider.client.channel(channelName)
            channel = ch

            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "consultation_requests"
                filter(filterColumn, FilterOperator.EQ, filterValue)
            }

            changeFlow.onEach { action ->
                val event = extractEvent(action)
                if (event != null) {
                    Log.d(TAG, "Realtime request event: $event")
                    _requestEvents.emit(event)
                }
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "Subscribed to $channelName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to $channelName", e)
        }
    }

    private fun extractEvent(action: PostgresAction): RequestRealtimeEvent? {
        val record: JsonObject = when (action) {
            is PostgresAction.Insert -> action.record
            is PostgresAction.Update -> action.record
            else -> return null
        }

        val requestId = record["request_id"]?.jsonPrimitive?.content ?: return null
        val status = record["status"]?.jsonPrimitive?.content ?: return null
        val consultationId = record["consultation_id"]?.jsonPrimitive?.content
        val patientSessionId = record["patient_session_id"]?.jsonPrimitive?.content
        val serviceType = record["service_type"]?.jsonPrimitive?.content

        return RequestRealtimeEvent(
            requestId = requestId,
            status = status,
            consultationId = consultationId,
            patientSessionId = patientSessionId,
            serviceType = serviceType,
        )
    }

    suspend fun unsubscribe() {
        try {
            channel?.unsubscribe()
            channel = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from request channel", e)
        }
    }

    companion object {
        private const val TAG = "ConsultReqRealtime"
    }
}

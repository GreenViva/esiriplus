package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.di.ApplicationScope
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicInteger
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
    val symptoms: String? = null,
    val patientAgeGroup: String? = null,
    val patientSex: String? = null,
    val patientBloodGroup: String? = null,
    val patientAllergies: String? = null,
    val patientChronicConditions: String? = null,
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
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val _requestEvents = MutableSharedFlow<RequestRealtimeEvent>(extraBufferCapacity = 16)
    val requestEvents: SharedFlow<RequestRealtimeEvent> = _requestEvents.asSharedFlow()

    private var channel: RealtimeChannel? = null
    private val subscriberCount = AtomicInteger(0)
    private var currentDoctorId: String? = null

    /**
     * Subscribe as a patient — listens for status changes on requests created by this session.
     */
    suspend fun subscribeAsPatient(patientSessionId: String, scope: CoroutineScope) {
        // Patient subscriptions always create a fresh channel (no ref counting)
        subscribeInternal(
            channelName = "patient-requests-$patientSessionId",
            filterColumn = "patient_session_id",
            filterValue = patientSessionId,
            scope = scope,
        )
    }

    /**
     * Subscribe as a doctor — listens for new incoming requests and status updates.
     * Uses reference counting so both IncomingRequestViewModel and DoctorOnlineService
     * can coexist on the same SharedFlow without fighting over the channel.
     */
    suspend fun subscribeAsDoctor(doctorId: String, scope: CoroutineScope) {
        if (currentDoctorId == doctorId && subscriberCount.get() > 0) {
            // Already subscribed for this doctor — just increment the reference count
            subscriberCount.incrementAndGet()
            Log.d(TAG, "Doctor subscription ref count incremented: ${subscriberCount.get()}")
            return
        }
        subscriberCount.set(1)
        currentDoctorId = doctorId
        subscribeInternal(
            channelName = "doctor-requests-$doctorId",
            filterColumn = "doctor_id",
            filterValue = doctorId,
            scope = scope,
        )
    }

    private suspend fun subscribeInternal(
        channelName: String,
        filterColumn: String,
        filterValue: String,
        scope: CoroutineScope,
    ) {
        try {
            // Actually tear down any existing channel
            channel?.let {
                try { it.unsubscribe() } catch (_: Exception) { }
            }
            channel = null

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
        val symptoms = record["symptoms"]?.jsonPrimitive?.content
        val patientAgeGroup = record["patient_age_group"]?.jsonPrimitive?.content
        val patientSex = record["patient_sex"]?.jsonPrimitive?.content
        val patientBloodGroup = record["patient_blood_group"]?.jsonPrimitive?.content
        val patientAllergies = record["patient_allergies"]?.jsonPrimitive?.content
        val patientChronicConditions = record["patient_chronic_conditions"]?.jsonPrimitive?.content

        return RequestRealtimeEvent(
            requestId = requestId,
            status = status,
            consultationId = consultationId,
            patientSessionId = patientSessionId,
            serviceType = serviceType,
            symptoms = symptoms,
            patientAgeGroup = patientAgeGroup,
            patientSex = patientSex,
            patientBloodGroup = patientBloodGroup,
            patientAllergies = patientAllergies,
            patientChronicConditions = patientChronicConditions,
        )
    }

    /**
     * Emit an incoming PENDING request directly (FCM fallback path).
     * Called by the FCM service when a consultation_request push arrives and
     * Realtime cannot be trusted (race condition before auth import, etc.).
     * The ViewModel deduplicates so double-firing is safe.
     */
    fun emitExternalRequest(requestId: String, serviceType: String?) {
        appScope.launch {
            _requestEvents.emit(
                RequestRealtimeEvent(
                    requestId = requestId,
                    status = "pending",
                    serviceType = serviceType,
                )
            )
        }
    }

    /**
     * Emit a resolved status for a request the doctor just acted on via the in-app UI.
     * Called by IncomingRequestViewModel after a successful HTTP accept/reject so that
     * DoctorOnlineService immediately stops ringing without waiting for a Realtime event.
     */
    fun emitRequestResolved(requestId: String, status: String) {
        appScope.launch {
            _requestEvents.emit(
                RequestRealtimeEvent(
                    requestId = requestId,
                    status = status,
                )
            )
        }
    }

    /**
     * Decrement the subscriber reference count. Only actually unsubscribes the
     * Realtime channel when the count reaches 0.
     */
    suspend fun unsubscribe() {
        val remaining = subscriberCount.decrementAndGet()
        Log.d(TAG, "Unsubscribe called, remaining subscribers: $remaining")
        if (remaining <= 0) {
            subscriberCount.set(0)
            currentDoctorId = null
            try {
                channel?.unsubscribe()
                channel = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unsubscribe from request channel", e)
            }
        }
    }

    /** Non-suspend variant safe to call from onCleared (where viewModelScope is cancelled). */
    fun unsubscribeSync() {
        val remaining = subscriberCount.decrementAndGet()
        Log.d(TAG, "UnsubscribeSync called, remaining subscribers: $remaining")
        if (remaining <= 0) {
            subscriberCount.set(0)
            currentDoctorId = null
            appScope.launch {
                try {
                    channel?.unsubscribe()
                    channel = null
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val TAG = "ConsultReqRealtime"
    }
}

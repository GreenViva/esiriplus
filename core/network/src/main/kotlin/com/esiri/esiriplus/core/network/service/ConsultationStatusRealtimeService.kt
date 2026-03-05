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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class ConsultationStatusEvent(
    val consultationId: String,
    val status: String,
    val scheduledEndAt: String? = null,
    val gracePeriodEndAt: String? = null,
    val extensionCount: Int = 0,
    val originalDurationMinutes: Int = 15,
)

@Singleton
class ConsultationStatusRealtimeService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val _statusEvents = MutableSharedFlow<ConsultationStatusEvent>(extraBufferCapacity = 16)
    val statusEvents: SharedFlow<ConsultationStatusEvent> = _statusEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val lock = Any()
    @Volatile private var channel: RealtimeChannel? = null

    private var reconnectJob: Job? = null
    private var currentConsultationId: String? = null
    private var currentScope: CoroutineScope? = null
    @Volatile private var intentionalUnsubscribe = false
    private var reconnectAttempt = 0

    private val backoffDelays = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)

    suspend fun subscribe(consultationId: String, scope: CoroutineScope) {
        synchronized(lock) {
            currentConsultationId = consultationId
            currentScope = scope
            reconnectAttempt = 0
            intentionalUnsubscribe = false
        }
        doSubscribe(consultationId, scope)
    }

    private suspend fun doSubscribe(consultationId: String, scope: CoroutineScope) {
        try {
            channel?.let { ch ->
                try { ch.unsubscribe() } catch (_: Exception) {}
            }
            synchronized(lock) { channel = null }

            _connectionState.value = RealtimeConnectionState.CONNECTING

            val ch = supabaseClientProvider.client.channel("consultation-status-$consultationId-${System.currentTimeMillis()}")
            synchronized(lock) { channel = ch }

            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "consultations"
                filter("consultation_id", FilterOperator.EQ, consultationId)
            }

            changeFlow.onEach { action ->
                val event = extractEvent(action)
                if (event != null) {
                    Log.d(TAG, "Consultation status event: $event")
                    _statusEvents.emit(event)
                }
            }.launchIn(scope)

            // Monitor channel status for unexpected disconnects
            ch.status.onEach { status ->
                Log.d(TAG, "Status channel: $status")
                when (status) {
                    RealtimeChannel.Status.SUBSCRIBED -> {
                        _connectionState.value = RealtimeConnectionState.CONNECTED
                        synchronized(lock) { reconnectAttempt = 0 }
                    }
                    RealtimeChannel.Status.UNSUBSCRIBED -> {
                        val shouldReconnect = synchronized(lock) {
                            !intentionalUnsubscribe && currentConsultationId != null
                        }
                        if (shouldReconnect) {
                            _connectionState.value = RealtimeConnectionState.DISCONNECTED
                            Log.w(TAG, "Status channel disconnected unexpectedly, scheduling reconnect")
                            scheduleReconnect()
                        }
                    }
                    else -> {}
                }
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "Subscribed to consultation status for $consultationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to consultation status", e)
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        synchronized(lock) {
            if (reconnectJob?.isActive == true) return
            val consultationId = currentConsultationId ?: return
            val scope = currentScope ?: return

            if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
                Log.e(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached, giving up")
                _connectionState.value = RealtimeConnectionState.DISCONNECTED
                return
            }

            val delayMs = backoffDelays[reconnectAttempt.coerceAtMost(backoffDelays.size - 1)]
            reconnectAttempt++

            reconnectJob = scope.launch {
                Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempt/$MAX_RECONNECT_ATTEMPTS)")
                delay(delayMs)
                if (!isActive) return@launch
                doSubscribe(consultationId, scope)
            }
        }
    }

    private fun extractEvent(action: PostgresAction): ConsultationStatusEvent? {
        val record: JsonObject = when (action) {
            is PostgresAction.Update -> action.record
            else -> return null
        }

        val consultationId = record["consultation_id"]?.jsonPrimitive?.content ?: return null
        val status = record["status"]?.jsonPrimitive?.content ?: return null
        val scheduledEndAt = record["scheduled_end_at"]?.jsonPrimitive?.content
        val gracePeriodEndAt = record["grace_period_end_at"]?.jsonPrimitive?.content
        val extensionCount = record["extension_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val originalDurationMinutes = record["original_duration_minutes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 15

        return ConsultationStatusEvent(
            consultationId = consultationId,
            status = status,
            scheduledEndAt = scheduledEndAt,
            gracePeriodEndAt = gracePeriodEndAt,
            extensionCount = extensionCount,
            originalDurationMinutes = originalDurationMinutes,
        )
    }

    suspend fun unsubscribe() {
        synchronized(lock) {
            intentionalUnsubscribe = true
            reconnectJob?.cancel()
            reconnectJob = null
            currentConsultationId = null
            currentScope = null
        }
        try {
            channel?.unsubscribe()
            synchronized(lock) { channel = null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from consultation status channel", e)
        }
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
    }

    /** Non-suspend variant safe to call from onCleared. */
    fun unsubscribeSync() {
        synchronized(lock) {
            intentionalUnsubscribe = true
            reconnectJob?.cancel()
            reconnectJob = null
            currentConsultationId = null
            currentScope = null
        }
        appScope.launch {
            try {
                channel?.unsubscribe()
                synchronized(lock) { channel = null }
            } catch (_: Exception) {}
        }
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
    }

    companion object {
        private const val TAG = "ConsultStatusRealtime"
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }
}

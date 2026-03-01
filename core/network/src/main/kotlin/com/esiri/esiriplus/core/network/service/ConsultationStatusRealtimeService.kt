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
) {

    private val _statusEvents = MutableSharedFlow<ConsultationStatusEvent>(extraBufferCapacity = 16)
    val statusEvents: SharedFlow<ConsultationStatusEvent> = _statusEvents.asSharedFlow()

    private var channel: RealtimeChannel? = null

    suspend fun subscribe(consultationId: String, scope: CoroutineScope) {
        try {
            unsubscribe()

            val ch = supabaseClientProvider.client.channel("consultation-status-$consultationId")
            channel = ch

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

            ch.subscribe()
            Log.d(TAG, "Subscribed to consultation status for $consultationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to consultation status", e)
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
        try {
            channel?.unsubscribe()
            channel = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from consultation status channel", e)
        }
    }

    companion object {
        private const val TAG = "ConsultStatusRealtime"
    }
}

package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.SupabaseClientProvider
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event emitted when a doctor's availability changes via Supabase Realtime.
 */
data class DoctorAvailabilityEvent(
    val doctorId: String,
    val isAvailable: Boolean,
)

/**
 * Patient-side realtime service that listens for doctor_profiles changes
 * (specifically is_available toggling) and emits events so the UI updates
 * instantly when a doctor goes online or offline.
 */
@Singleton
class DoctorAvailabilityRealtimeService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {

    private val _availabilityEvents = MutableSharedFlow<DoctorAvailabilityEvent>(extraBufferCapacity = 32)
    val availabilityEvents: SharedFlow<DoctorAvailabilityEvent> = _availabilityEvents.asSharedFlow()

    @Volatile
    private var channel: RealtimeChannel? = null

    /**
     * Subscribe to all doctor_profiles changes (unfiltered — the patient
     * needs updates for any doctor in the displayed specialty list).
     */
    suspend fun subscribe(scope: CoroutineScope) {
        unsubscribe()

        try {
            val ch = supabaseClientProvider.client.channel(
                "doctor-availability-${System.currentTimeMillis()}",
            )
            channel = ch

            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "doctor_profiles"
            }

            changeFlow.onEach { action ->
                val event = extractEvent(action)
                if (event != null) {
                    Log.d(TAG, "Doctor ${event.doctorId} is now ${if (event.isAvailable) "ONLINE" else "OFFLINE"}")
                    _availabilityEvents.emit(event)
                }
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "Subscribed to doctor availability realtime")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to doctor availability realtime", e)
        }
    }

    suspend fun unsubscribe() {
        try {
            channel?.unsubscribe()
        } catch (_: Exception) {}
        channel = null
    }

    private fun extractEvent(action: PostgresAction): DoctorAvailabilityEvent? {
        val record = when (action) {
            is PostgresAction.Update -> action.record
            is PostgresAction.Insert -> action.record
            else -> return null
        }

        val doctorId = record["doctor_id"]?.jsonPrimitive?.content ?: return null
        val isAvailable = record["is_available"]?.jsonPrimitive?.booleanOrNull ?: return null

        return DoctorAvailabilityEvent(doctorId = doctorId, isAvailable = isAvailable)
    }

    companion object {
        private const val TAG = "DoctorAvailabilityRT"
    }
}

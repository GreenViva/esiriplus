package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorRealtimeService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {

    private val _consultationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val consultationEvents: SharedFlow<Unit> = _consultationEvents.asSharedFlow()

    private var channel: RealtimeChannel? = null

    suspend fun subscribeToConsultations(doctorId: String, scope: CoroutineScope) {
        try {
            unsubscribe()

            val ch = supabaseClientProvider.client.channel("doctor-consultations-$doctorId")
            channel = ch

            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "consultations"
                filter("doctor_id", FilterOperator.EQ, doctorId)
            }

            changeFlow.onEach { action ->
                Log.d(TAG, "Realtime event: ${action::class.simpleName}")
                _consultationEvents.emit(Unit)
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "Subscribed to realtime consultations for doctor $doctorId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to realtime consultations", e)
        }
    }

    suspend fun unsubscribe() {
        try {
            channel?.unsubscribe()
            channel = null
            Log.d(TAG, "Unsubscribed from realtime consultations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from realtime", e)
        }
    }

    companion object {
        private const val TAG = "DoctorRealtimeSvc"
    }
}

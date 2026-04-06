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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorRealtimeService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val _consultationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val consultationEvents: SharedFlow<Unit> = _consultationEvents.asSharedFlow()

    private val _profileEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val profileEvents: SharedFlow<Unit> = _profileEvents.asSharedFlow()

    private val _earningsEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val earningsEvents: SharedFlow<Unit> = _earningsEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val lock = Any()
    @Volatile private var channel: RealtimeChannel? = null
    @Volatile private var profileChannel: RealtimeChannel? = null
    @Volatile private var earningsChannel: RealtimeChannel? = null

    private var reconnectJob: Job? = null
    private var currentDoctorId: String? = null
    private var currentScope: CoroutineScope? = null
    @Volatile private var intentionalUnsubscribe = false
    private var reconnectAttempt = 0

    private val backoffDelays = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)

    suspend fun subscribeToConsultations(doctorId: String, scope: CoroutineScope) {
        synchronized(lock) {
            currentDoctorId = doctorId
            currentScope = scope
            reconnectAttempt = 0
            intentionalUnsubscribe = false
        }
        doSubscribeConsultations(doctorId, scope)
    }

    private suspend fun doSubscribeConsultations(doctorId: String, scope: CoroutineScope) {
        try {
            channel?.let { ch ->
                try { ch.unsubscribe() } catch (_: Exception) {}
            }
            synchronized(lock) { channel = null }

            _connectionState.value = RealtimeConnectionState.CONNECTING

            val ch = supabaseClientProvider.client.channel("doctor-consultations-$doctorId-${System.currentTimeMillis()}")
            synchronized(lock) { channel = ch }

            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "consultations"
                filter("doctor_id", FilterOperator.EQ, doctorId)
            }

            changeFlow.onEach { action ->
                Log.d(TAG, "Realtime event: ${action::class.simpleName}")
                _consultationEvents.emit(Unit)
            }.launchIn(scope)

            // Monitor channel status for unexpected disconnects
            ch.status.onEach { status ->
                Log.d(TAG, "Consultations channel status: $status")
                when (status) {
                    RealtimeChannel.Status.SUBSCRIBED -> {
                        _connectionState.value = RealtimeConnectionState.CONNECTED
                        synchronized(lock) { reconnectAttempt = 0 }
                    }
                    RealtimeChannel.Status.UNSUBSCRIBED -> {
                        val shouldReconnect = synchronized(lock) {
                            !intentionalUnsubscribe && currentDoctorId != null
                        }
                        if (shouldReconnect) {
                            _connectionState.value = RealtimeConnectionState.DISCONNECTED
                            Log.w(TAG, "Consultations channel disconnected unexpectedly, scheduling reconnect")
                            scheduleReconnect()
                        }
                    }
                    else -> {}
                }
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "Subscribed to realtime consultations for doctor $doctorId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to realtime consultations", e)
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    suspend fun subscribeToProfileChanges(doctorId: String, scope: CoroutineScope) {
        doSubscribeProfile(doctorId, scope)
    }

    private suspend fun doSubscribeProfile(doctorId: String, scope: CoroutineScope) {
        try {
            profileChannel?.let { ch ->
                try { ch.unsubscribe() } catch (_: Exception) {}
            }
            synchronized(lock) { profileChannel = null }

            val ch = supabaseClientProvider.client.channel("doctor-profile-$doctorId-${System.currentTimeMillis()}")
            synchronized(lock) { profileChannel = ch }

            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "doctor_profiles"
                filter("doctor_id", FilterOperator.EQ, doctorId)
            }

            changeFlow.onEach { action ->
                Log.d(TAG, "Profile realtime event: ${action::class.simpleName}")
                _profileEvents.emit(Unit)
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "Subscribed to realtime profile changes for doctor $doctorId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to realtime profile changes", e)
        }
    }

    suspend fun subscribeToEarnings(doctorId: String, scope: CoroutineScope) {
        doSubscribeEarnings(doctorId, scope)
    }

    private suspend fun doSubscribeEarnings(doctorId: String, scope: CoroutineScope) {
        try {
            earningsChannel?.let { ch ->
                try { ch.unsubscribe() } catch (_: Exception) {}
            }
            synchronized(lock) { earningsChannel = null }

            val ch = supabaseClientProvider.client.channel("doctor-earnings-$doctorId-${System.currentTimeMillis()}")
            synchronized(lock) { earningsChannel = ch }

            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "doctor_earnings"
                filter("doctor_id", FilterOperator.EQ, doctorId)
            }

            changeFlow.onEach { action ->
                Log.d(TAG, "Earnings realtime event: ${action::class.simpleName}")
                _earningsEvents.emit(Unit)
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "Subscribed to realtime earnings for doctor $doctorId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to realtime earnings", e)
        }
    }

    private fun scheduleReconnect() {
        synchronized(lock) {
            if (reconnectJob?.isActive == true) return
            val doctorId = currentDoctorId ?: return
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
                doSubscribeConsultations(doctorId, scope)
                doSubscribeProfile(doctorId, scope)
                doSubscribeEarnings(doctorId, scope)
            }
        }
    }

    suspend fun unsubscribe() {
        try {
            channel?.unsubscribe()
            synchronized(lock) { channel = null }
            Log.d(TAG, "Unsubscribed from realtime consultations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from realtime", e)
        }
    }

    private suspend fun unsubscribeProfile() {
        try {
            profileChannel?.unsubscribe()
            synchronized(lock) { profileChannel = null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from profile realtime", e)
        }
    }

    private suspend fun unsubscribeEarnings() {
        try {
            earningsChannel?.unsubscribe()
            synchronized(lock) { earningsChannel = null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from earnings realtime", e)
        }
    }

    suspend fun unsubscribeAll() {
        synchronized(lock) {
            intentionalUnsubscribe = true
            reconnectJob?.cancel()
            reconnectJob = null
            currentDoctorId = null
            currentScope = null
        }
        unsubscribe()
        unsubscribeProfile()
        unsubscribeEarnings()
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
    }

    /** Non-suspend variant safe to call from onCleared (where viewModelScope is cancelled). */
    fun unsubscribeAllSync() {
        synchronized(lock) {
            intentionalUnsubscribe = true
            reconnectJob?.cancel()
            reconnectJob = null
            currentDoctorId = null
            currentScope = null
        }
        appScope.launch {
            try {
                unsubscribeAll()
            } catch (_: Exception) { }
        }
    }

    companion object {
        private const val TAG = "DoctorRealtimeSvc"
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }
}

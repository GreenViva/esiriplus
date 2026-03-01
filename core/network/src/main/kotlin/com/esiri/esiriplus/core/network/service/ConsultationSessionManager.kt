package com.esiri.esiriplus.core.network.service

import android.os.SystemClock
import android.util.Log
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.domain.model.ConsultationPhase
import com.esiri.esiriplus.core.domain.model.ConsultationSessionState
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsultationSessionManager @Inject constructor(
    private val timerService: ConsultationTimerService,
    private val statusRealtimeService: ConsultationStatusRealtimeService,
    private val consultationDao: ConsultationDao,
) {
    private val _state = MutableStateFlow(ConsultationSessionState())
    val state: StateFlow<ConsultationSessionState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var realtimeJob: Job? = null
    private var scope: CoroutineScope? = null
    private var currentConsultationId: String? = null

    // Monotonic anchor: elapsedRealtime at the moment we synced
    private var anchorElapsedMs: Long = 0L
    private var anchorRemainingMs: Long = 0L

    fun start(consultationId: String, scope: CoroutineScope) {
        this.scope = scope
        this.currentConsultationId = consultationId

        _state.update {
            ConsultationSessionState(
                consultationId = consultationId,
                isLoading = true,
            )
        }

        scope.launch {
            syncWithServer(consultationId)
        }

        // Subscribe to realtime status changes
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            statusRealtimeService.subscribe(consultationId, scope)
            statusRealtimeService.statusEvents.collect { event ->
                if (event.consultationId == consultationId) {
                    handleRealtimeEvent(event)
                }
            }
        }
    }

    private suspend fun syncWithServer(consultationId: String) {
        when (val result = timerService.sync(consultationId)) {
            is ApiResult.Success -> {
                val data = result.data
                val serverTimeMs = parseIso(data.serverTime)
                val scheduledEndMs = data.scheduledEndAt?.let { parseIso(it) } ?: 0L
                val gracePeriodEndMs = data.gracePeriodEndAt?.let { parseIso(it) } ?: 0L
                val phase = mapStatusToPhase(data.status)

                val remainingMs = when (phase) {
                    ConsultationPhase.ACTIVE -> {
                        if (scheduledEndMs > 0L) {
                            (scheduledEndMs - serverTimeMs).coerceAtLeast(0L)
                        } else {
                            // Fallback: scheduled_end_at is null (consultation created
                            // without timer fields). Use originalDurationMinutes as
                            // the full session duration to avoid immediate expiry.
                            Log.w(TAG, "scheduled_end_at is null for ACTIVE consultation, using duration fallback")
                            val sessionStartMs = data.sessionStartTime?.let { parseIso(it) } ?: 0L
                            if (sessionStartMs > 0L) {
                                val endMs = sessionStartMs + data.originalDurationMinutes * 60_000L
                                (endMs - serverTimeMs).coerceAtLeast(0L)
                            } else {
                                // Last resort: assume session just started
                                data.originalDurationMinutes * 60_000L
                            }
                        }
                    }
                    ConsultationPhase.GRACE_PERIOD -> (gracePeriodEndMs - serverTimeMs).coerceAtLeast(0L)
                    else -> 0L
                }

                anchorElapsedMs = SystemClock.elapsedRealtime()
                anchorRemainingMs = remainingMs

                _state.update {
                    it.copy(
                        consultationId = consultationId,
                        phase = phase,
                        remainingSeconds = (remainingMs / 1000).toInt(),
                        totalDurationMinutes = data.originalDurationMinutes,
                        originalDurationMinutes = data.originalDurationMinutes,
                        extensionCount = data.extensionCount,
                        serviceType = data.serviceType,
                        consultationFee = data.consultationFee,
                        scheduledEndAtEpochMs = scheduledEndMs,
                        gracePeriodEndAtEpochMs = gracePeriodEndMs,
                        isLoading = false,
                        error = null,
                    )
                }

                // Persist to Room for crash recovery
                persistTimerState(consultationId, data)

                // Start countdown if in a ticking phase
                if (phase == ConsultationPhase.ACTIVE || phase == ConsultationPhase.GRACE_PERIOD) {
                    startCountdown()
                }
            }
            is ApiResult.Error -> {
                _state.update { it.copy(isLoading = false, error = result.message) }
            }
            is ApiResult.NetworkError -> {
                _state.update { it.copy(isLoading = false, error = result.message) }
            }
            is ApiResult.Unauthorized -> {
                _state.update { it.copy(isLoading = false, error = "Unauthorized") }
            }
        }
    }

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = scope?.launch {
            while (true) {
                delay(1000)
                val elapsed = SystemClock.elapsedRealtime() - anchorElapsedMs
                val remainingMs = (anchorRemainingMs - elapsed).coerceAtLeast(0L)
                val remainingSec = (remainingMs / 1000).toInt()

                _state.update { it.copy(remainingSeconds = remainingSec) }

                if (remainingSec <= 0) {
                    onTimerReachedZero()
                    break
                }
            }
        }
    }

    private suspend fun onTimerReachedZero() {
        val cid = currentConsultationId ?: return
        val currentPhase = _state.value.phase

        when (currentPhase) {
            ConsultationPhase.ACTIVE -> {
                // Timer expired — call server
                when (val result = timerService.timerExpired(cid)) {
                    is ApiResult.Success -> {
                        val data = result.data
                        _state.update {
                            it.copy(
                                phase = mapStatusToPhase(data.status),
                                remainingSeconds = 0,
                            )
                        }
                        persistTimerState(cid, data)
                    }
                    else -> {
                        // Fallback: set local state even if server call fails
                        _state.update {
                            it.copy(
                                phase = ConsultationPhase.AWAITING_EXTENSION,
                                remainingSeconds = 0,
                            )
                        }
                    }
                }
            }
            ConsultationPhase.GRACE_PERIOD -> {
                // Grace period expired — server cron will handle,
                // but set local state immediately
                _state.update {
                    it.copy(
                        phase = ConsultationPhase.AWAITING_EXTENSION,
                        remainingSeconds = 0,
                        gracePeriodEndAtEpochMs = 0L,
                    )
                }
            }
            else -> { /* no-op */ }
        }
    }

    private fun handleRealtimeEvent(event: ConsultationStatusEvent) {
        val phase = mapStatusToPhase(event.status)
        val scheduledEndMs = event.scheduledEndAt?.let { parseIso(it) } ?: _state.value.scheduledEndAtEpochMs
        val gracePeriodEndMs = event.gracePeriodEndAt?.let { parseIso(it) } ?: 0L

        _state.update {
            it.copy(
                phase = phase,
                extensionCount = event.extensionCount,
                originalDurationMinutes = event.originalDurationMinutes,
                scheduledEndAtEpochMs = scheduledEndMs,
                gracePeriodEndAtEpochMs = gracePeriodEndMs,
            )
        }

        // If transitioning back to active (extension granted), re-sync timer
        if (phase == ConsultationPhase.ACTIVE || phase == ConsultationPhase.GRACE_PERIOD) {
            scope?.launch { syncWithServer(event.consultationId) }
        }

        if (phase == ConsultationPhase.COMPLETED) {
            timerJob?.cancel()
        }
    }

    // ── Action methods (called from ViewModels) ──────────────────────────────

    suspend fun endConsultation(): ApiResult<ConsultationSyncResponse> {
        val cid = currentConsultationId ?: return ApiResult.Error(0, "No active consultation")
        val result = timerService.endConsultation(cid)
        if (result is ApiResult.Success) {
            timerJob?.cancel()
            _state.update { it.copy(phase = ConsultationPhase.COMPLETED, remainingSeconds = 0) }
        }
        return result
    }

    suspend fun requestExtension(): ApiResult<ConsultationSyncResponse> {
        val cid = currentConsultationId ?: return ApiResult.Error(0, "No active consultation")
        val result = timerService.requestExtension(cid)
        if (result is ApiResult.Success) {
            _state.update { it.copy(extensionRequested = true) }
        }
        return result
    }

    suspend fun acceptExtension(): ApiResult<ConsultationSyncResponse> {
        val cid = currentConsultationId ?: return ApiResult.Error(0, "No active consultation")
        val result = timerService.acceptExtension(cid)
        if (result is ApiResult.Success) {
            val data = result.data
            val phase = mapStatusToPhase(data.status)
            val gracePeriodEndMs = data.gracePeriodEndAt?.let { parseIso(it) } ?: 0L
            val serverTimeMs = parseIso(data.serverTime)
            val remainingMs = (gracePeriodEndMs - serverTimeMs).coerceAtLeast(0L)

            anchorElapsedMs = SystemClock.elapsedRealtime()
            anchorRemainingMs = remainingMs

            _state.update {
                it.copy(
                    phase = phase,
                    gracePeriodEndAtEpochMs = gracePeriodEndMs,
                    remainingSeconds = (remainingMs / 1000).toInt(),
                )
            }
            startCountdown()
        }
        return result
    }

    suspend fun declineExtension(): ApiResult<ConsultationSyncResponse> {
        val cid = currentConsultationId ?: return ApiResult.Error(0, "No active consultation")
        val result = timerService.declineExtension(cid)
        if (result is ApiResult.Success) {
            _state.update { it.copy(patientDeclined = true) }
        }
        return result
    }

    suspend fun paymentConfirmed(paymentId: String): ApiResult<ConsultationSyncResponse> {
        val cid = currentConsultationId ?: return ApiResult.Error(0, "No active consultation")
        val result = timerService.paymentConfirmed(cid, paymentId)
        if (result is ApiResult.Success) {
            val data = result.data
            _state.update {
                it.copy(
                    extensionRequested = false,
                    patientDeclined = false,
                )
            }
            // Re-sync to reset timer with new scheduledEndAt
            syncWithServer(cid)
        }
        return result
    }

    suspend fun cancelPayment(): ApiResult<ConsultationSyncResponse> {
        val cid = currentConsultationId ?: return ApiResult.Error(0, "No active consultation")
        val result = timerService.cancelPayment(cid)
        if (result is ApiResult.Success) {
            timerJob?.cancel()
            _state.update {
                it.copy(
                    phase = ConsultationPhase.AWAITING_EXTENSION,
                    remainingSeconds = 0,
                    gracePeriodEndAtEpochMs = 0L,
                )
            }
        }
        return result
    }

    fun stop() {
        timerJob?.cancel()
        realtimeJob?.cancel()
        scope?.launch {
            statusRealtimeService.unsubscribe()
        }
        currentConsultationId = null
        _state.value = ConsultationSessionState()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun persistTimerState(
        consultationId: String,
        data: ConsultationSyncResponse,
    ) {
        try {
            consultationDao.updateTimerState(
                consultationId = consultationId,
                scheduledEndAt = data.scheduledEndAt?.let { parseIso(it) },
                extensionCount = data.extensionCount,
                gracePeriodEndAt = data.gracePeriodEndAt?.let { parseIso(it) },
                originalDurationMinutes = data.originalDurationMinutes,
                status = data.status.uppercase(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist timer state to Room", e)
        }
    }

    private fun mapStatusToPhase(status: String): ConsultationPhase {
        return when (status.lowercase()) {
            "active" -> ConsultationPhase.ACTIVE
            "awaiting_extension" -> ConsultationPhase.AWAITING_EXTENSION
            "grace_period" -> ConsultationPhase.GRACE_PERIOD
            "completed" -> ConsultationPhase.COMPLETED
            else -> ConsultationPhase.COMPLETED
        }
    }

    private fun parseIso(iso: String): Long {
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ISO date: $iso", e)
            0L
        }
    }

    companion object {
        private const val TAG = "ConsultSessionMgr"
    }
}

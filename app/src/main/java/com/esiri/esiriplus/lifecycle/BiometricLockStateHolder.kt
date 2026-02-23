package com.esiri.esiriplus.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricLockStateHolder @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    @Volatile
    private var lastActivityTimestamp: Long = System.currentTimeMillis()

    private var inactivityJob: Job? = null

    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
        if (locked) {
            inactivityJob?.cancel()
            inactivityJob = null
        }
    }

    fun unlock() {
        _isLocked.value = false
        lastActivityTimestamp = System.currentTimeMillis()
        startInactivityTimer()
    }

    fun onUserActivity() {
        if (!_isLocked.value) {
            lastActivityTimestamp = System.currentTimeMillis()
        }
    }

    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            while (true) {
                delay(CHECK_INTERVAL_MS)
                val elapsed = System.currentTimeMillis() - lastActivityTimestamp
                if (elapsed >= INACTIVITY_TIMEOUT_MS) {
                    setLocked(true)
                    break
                }
            }
        }
    }

    companion object {
        private const val INACTIVITY_TIMEOUT_MS = 180_000L // 3 minutes
        private const val CHECK_INTERVAL_MS = 30_000L // check every 30 seconds
    }
}

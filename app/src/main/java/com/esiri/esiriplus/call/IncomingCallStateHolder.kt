package com.esiri.esiriplus.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingCall(
    val consultationId: String,
    val roomId: String,
    val callType: String,
    val callerRole: String,
    /** Medication-reminder event id (only set for caller_role=medication_reminder_nurse). */
    val medReminderEventId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Singleton
class IncomingCallStateHolder @Inject constructor() {

    private val _incomingCall = MutableStateFlow<IncomingCall?>(null)
    val incomingCall: StateFlow<IncomingCall?> = _incomingCall.asStateFlow()

    fun showIncomingCall(call: IncomingCall) {
        _incomingCall.value = call
    }

    fun dismiss() {
        _incomingCall.value = null
    }
}

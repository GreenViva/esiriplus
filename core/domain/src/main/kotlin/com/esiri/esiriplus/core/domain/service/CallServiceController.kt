package com.esiri.esiriplus.core.domain.service

/**
 * Controls the call foreground service from feature modules without depending on :app.
 * Implementation lives in app module, bound via Hilt.
 */
interface CallServiceController {
    fun startCallService(consultationId: String, callType: String, isVideo: Boolean)
    fun stopCallService()
    fun updateCallDuration(seconds: Int)
}

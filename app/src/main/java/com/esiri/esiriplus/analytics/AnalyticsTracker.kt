package com.esiri.esiriplus.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsTracker @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
) {

    fun trackLogin(method: String) {
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    fun trackSignUp(method: String) {
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    fun trackConsultationCreated(serviceType: String) {
        firebaseAnalytics.logEvent("consultation_created", Bundle().apply {
            putString("service_type", serviceType)
        })
    }

    fun trackConsultationCompleted(durationMinutes: Long) {
        firebaseAnalytics.logEvent("consultation_completed", Bundle().apply {
            putLong("duration_minutes", durationMinutes)
        })
    }

    fun trackPaymentInitiated(amountTzs: Long, method: String) {
        firebaseAnalytics.logEvent("payment_initiated", Bundle().apply {
            putLong("amount_tzs", amountTzs)
            putString("payment_method", method)
        })
    }

    fun trackPaymentCompleted(amountTzs: Long) {
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.PURCHASE, Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, amountTzs.toDouble())
            putString(FirebaseAnalytics.Param.CURRENCY, "TZS")
        })
    }

    fun trackPaymentFailed(reason: String) {
        firebaseAnalytics.logEvent("payment_failed", Bundle().apply {
            putString("reason", reason)
        })
    }

    fun trackVideoCallStarted(callType: String) {
        firebaseAnalytics.logEvent("video_call_started", Bundle().apply {
            putString("call_type", callType)
        })
    }

    fun trackVideoCallEnded(durationSeconds: Long) {
        firebaseAnalytics.logEvent("video_call_ended", Bundle().apply {
            putLong("duration_seconds", durationSeconds)
        })
    }

    fun trackDoctorSearched(category: String) {
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SEARCH, Bundle().apply {
            putString(FirebaseAnalytics.Param.SEARCH_TERM, category)
        })
    }

    fun trackScreenView(screenName: String) {
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        })
    }

    fun trackError(errorType: String, errorMessage: String) {
        firebaseAnalytics.logEvent("app_error", Bundle().apply {
            putString("error_type", errorType)
            putString("error_message", errorMessage.take(MAX_PARAM_LENGTH))
        })
    }

    fun trackDoctorOnlineToggle(isOnline: Boolean) {
        firebaseAnalytics.logEvent("doctor_online_toggle", Bundle().apply {
            putBoolean("is_online", isOnline)
        })
    }

    fun trackAppointmentBooked(serviceType: String) {
        firebaseAnalytics.logEvent("appointment_booked", Bundle().apply {
            putString("service_type", serviceType)
        })
    }

    fun trackSessionExtended() {
        firebaseAnalytics.logEvent("session_extended", null)
    }

    companion object {
        private const val MAX_PARAM_LENGTH = 100
    }
}

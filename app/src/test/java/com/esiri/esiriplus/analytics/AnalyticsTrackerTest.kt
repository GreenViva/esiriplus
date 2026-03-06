package com.esiri.esiriplus.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class AnalyticsTrackerTest {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var tracker: AnalyticsTracker

    @Before
    fun setup() {
        firebaseAnalytics = mockk(relaxed = true)
        tracker = AnalyticsTracker(firebaseAnalytics)
    }

    @Test
    fun `trackLogin logs LOGIN event`() {
        tracker.trackLogin("doctor_email")
        verify { firebaseAnalytics.logEvent(eq(FirebaseAnalytics.Event.LOGIN), any()) }
    }

    @Test
    fun `trackSignUp logs SIGN_UP event`() {
        tracker.trackSignUp("patient_id")
        verify { firebaseAnalytics.logEvent(eq(FirebaseAnalytics.Event.SIGN_UP), any()) }
    }

    @Test
    fun `trackConsultationCreated logs custom event`() {
        tracker.trackConsultationCreated("general")
        verify { firebaseAnalytics.logEvent(eq("consultation_created"), any()) }
    }

    @Test
    fun `trackPaymentCompleted logs PURCHASE event`() {
        tracker.trackPaymentCompleted(5000L)
        verify { firebaseAnalytics.logEvent(eq(FirebaseAnalytics.Event.PURCHASE), any()) }
    }

    @Test
    fun `trackPaymentFailed logs payment_failed event`() {
        tracker.trackPaymentFailed("insufficient_funds")
        verify { firebaseAnalytics.logEvent(eq("payment_failed"), any()) }
    }

    @Test
    fun `trackError logs app_error event`() {
        tracker.trackError("network", "Connection timed out")
        verify { firebaseAnalytics.logEvent(eq("app_error"), any()) }
    }

    @Test
    fun `trackVideoCallStarted logs video_call_started event`() {
        tracker.trackVideoCallStarted("video")
        verify { firebaseAnalytics.logEvent(eq("video_call_started"), any()) }
    }

    @Test
    fun `trackVideoCallEnded logs video_call_ended event`() {
        tracker.trackVideoCallEnded(120L)
        verify { firebaseAnalytics.logEvent(eq("video_call_ended"), any()) }
    }

    @Test
    fun `trackScreenView logs SCREEN_VIEW event`() {
        tracker.trackScreenView("DoctorDashboard")
        verify { firebaseAnalytics.logEvent(eq(FirebaseAnalytics.Event.SCREEN_VIEW), any()) }
    }

    @Test
    fun `trackDoctorOnlineToggle logs toggle event`() {
        tracker.trackDoctorOnlineToggle(true)
        verify { firebaseAnalytics.logEvent(eq("doctor_online_toggle"), any()) }
    }

    @Test
    fun `trackSessionExtended logs session_extended event`() {
        tracker.trackSessionExtended()
        verify { firebaseAnalytics.logEvent(eq("session_extended"), any()) }
    }

    @Test
    fun `trackAppointmentBooked logs appointment_booked event`() {
        tracker.trackAppointmentBooked("specialist")
        verify { firebaseAnalytics.logEvent(eq("appointment_booked"), any()) }
    }

    @Test
    fun `trackDoctorSearched logs SEARCH event`() {
        tracker.trackDoctorSearched("gp")
        verify { firebaseAnalytics.logEvent(eq(FirebaseAnalytics.Event.SEARCH), any()) }
    }
}

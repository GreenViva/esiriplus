package com.esiri.esiriplus.fcm

import com.esiri.esiriplus.fcm.FcmMessageRouter.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class FcmMessageRouterTest {

    // --- Routing tests ---

    @Test
    fun `consultation request skipped when service is running`() {
        val route = FcmMessageRouter.route(
            type = "CONSULTATION_REQUEST",
            notificationId = "n-1",
            hasTitle = false,
            isDoctorOnlineServiceRunning = true,
        )
        assertEquals(Route.SKIP_DUPLICATE, route)
    }

    @Test
    fun `consultation request falls back when service is not running`() {
        val route = FcmMessageRouter.route(
            type = "CONSULTATION_REQUEST",
            notificationId = "n-1",
            hasTitle = false,
            isDoctorOnlineServiceRunning = false,
        )
        assertEquals(Route.CONSULTATION_REQUEST_FALLBACK, route)
    }

    @Test
    fun `consultation request case insensitive`() {
        val route = FcmMessageRouter.route(
            type = "consultation_request",
            notificationId = null,
            hasTitle = false,
            isDoctorOnlineServiceRunning = false,
        )
        assertEquals(Route.CONSULTATION_REQUEST_FALLBACK, route)
    }

    @Test
    fun `video call incoming routes to INCOMING_CALL`() {
        val route = FcmMessageRouter.route(
            type = "VIDEO_CALL_INCOMING",
            notificationId = null,
            hasTitle = false,
            isDoctorOnlineServiceRunning = false,
        )
        assertEquals(Route.INCOMING_CALL, route)
    }

    @Test
    fun `message with notificationId routes to SECURE_FETCH`() {
        val route = FcmMessageRouter.route(
            type = "MESSAGE_RECEIVED",
            notificationId = "n-123",
            hasTitle = false,
            isDoctorOnlineServiceRunning = false,
        )
        assertEquals(Route.SECURE_FETCH, route)
    }

    @Test
    fun `message without notificationId but with title routes to INLINE`() {
        val route = FcmMessageRouter.route(
            type = "GENERAL",
            notificationId = null,
            hasTitle = true,
            isDoctorOnlineServiceRunning = false,
        )
        assertEquals(Route.INLINE_NOTIFICATION, route)
    }

    @Test
    fun `message with no notificationId and no title is dropped`() {
        val route = FcmMessageRouter.route(
            type = "GENERAL",
            notificationId = null,
            hasTitle = false,
            isDoctorOnlineServiceRunning = false,
        )
        assertEquals(Route.DROP, route)
    }

    // --- Generic title key tests ---

    @Test
    fun `genericTitleKey maps consultation request`() {
        assertEquals("notification_new_consultation", FcmMessageRouter.genericTitleKey("CONSULTATION_REQUEST"))
    }

    @Test
    fun `genericTitleKey maps consultation accepted`() {
        assertEquals("notification_consultation_accepted", FcmMessageRouter.genericTitleKey("CONSULTATION_ACCEPTED"))
    }

    @Test
    fun `genericTitleKey maps message received`() {
        assertEquals("notification_new_message", FcmMessageRouter.genericTitleKey("MESSAGE_RECEIVED"))
    }

    @Test
    fun `genericTitleKey maps video call`() {
        assertEquals("notification_incoming_video_call", FcmMessageRouter.genericTitleKey("VIDEO_CALL_INCOMING"))
    }

    @Test
    fun `genericTitleKey maps report ready`() {
        assertEquals("notification_report_ready", FcmMessageRouter.genericTitleKey("REPORT_READY"))
    }

    @Test
    fun `genericTitleKey maps payment status`() {
        assertEquals("notification_payment_update", FcmMessageRouter.genericTitleKey("PAYMENT_STATUS"))
    }

    @Test
    fun `genericTitleKey maps doctor approved and rejected`() {
        assertEquals("notification_application_update", FcmMessageRouter.genericTitleKey("DOCTOR_APPROVED"))
        assertEquals("notification_application_update", FcmMessageRouter.genericTitleKey("DOCTOR_REJECTED"))
    }

    @Test
    fun `genericTitleKey maps doctor warned`() {
        assertEquals("notification_admin_warning", FcmMessageRouter.genericTitleKey("DOCTOR_WARNED"))
    }

    @Test
    fun `genericTitleKey maps doctor suspended`() {
        assertEquals("notification_account_suspended", FcmMessageRouter.genericTitleKey("DOCTOR_SUSPENDED"))
    }

    @Test
    fun `genericTitleKey maps doctor unsuspended and unbanned`() {
        assertEquals("notification_account_reinstated", FcmMessageRouter.genericTitleKey("DOCTOR_UNSUSPENDED"))
        assertEquals("notification_account_reinstated", FcmMessageRouter.genericTitleKey("DOCTOR_UNBANNED"))
    }

    @Test
    fun `genericTitleKey maps doctor banned`() {
        assertEquals("notification_account_banned", FcmMessageRouter.genericTitleKey("DOCTOR_BANNED"))
    }

    @Test
    fun `genericTitleKey returns default for unknown type`() {
        assertEquals("notification_default_title", FcmMessageRouter.genericTitleKey("UNKNOWN_TYPE"))
    }

    @Test
    fun `genericTitleKey is case insensitive`() {
        assertEquals("notification_new_consultation", FcmMessageRouter.genericTitleKey("consultation_request"))
    }
}

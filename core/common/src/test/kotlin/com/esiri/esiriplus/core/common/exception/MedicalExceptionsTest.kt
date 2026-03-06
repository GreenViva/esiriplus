package com.esiri.esiriplus.core.common.exception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalExceptionsTest {

    @Test
    fun `ConsultationException carries consultationId`() {
        val ex = ConsultationException("test", consultationId = "c-123")
        assertEquals("test", ex.message)
        assertEquals("c-123", ex.consultationId)
        assertNull(ex.cause)
        assertTrue(ex is EsiriplusException)
    }

    @Test
    fun `PaymentException carries payment details`() {
        val cause = RuntimeException("root")
        val ex = PaymentException("payment failed", cause, "ref-456", 5000L)
        assertEquals("payment failed", ex.message)
        assertEquals(cause, ex.cause)
        assertEquals("ref-456", ex.paymentReference)
        assertEquals(5000L, ex.amountTzs)
    }

    @Test
    fun `SessionException carries sessionId`() {
        val ex = SessionException("expired", sessionId = "s-789")
        assertEquals("s-789", ex.sessionId)
    }

    @Test
    fun `AuthenticationException is an EsiriplusException`() {
        val ex = AuthenticationException("auth failed")
        assertTrue(ex is EsiriplusException)
        assertTrue(ex is Exception)
    }

    @Test
    fun `DoctorProfileException carries doctorId`() {
        val ex = DoctorProfileException("not found", doctorId = "d-001")
        assertEquals("d-001", ex.doctorId)
    }

    @Test
    fun `AppointmentException carries appointmentId`() {
        val ex = AppointmentException("cancelled", appointmentId = "a-002")
        assertEquals("a-002", ex.appointmentId)
    }

    @Test
    fun `VideoCallException carries meetingId`() {
        val ex = VideoCallException("failed", meetingId = "m-003")
        assertEquals("m-003", ex.meetingId)
    }

    @Test
    fun `FileUploadException carries file details`() {
        val ex = FileUploadException("too large", filePath = "/tmp/img.png", maxSizeMb = 10)
        assertEquals("/tmp/img.png", ex.filePath)
        assertEquals(10, ex.maxSizeMb)
    }
}

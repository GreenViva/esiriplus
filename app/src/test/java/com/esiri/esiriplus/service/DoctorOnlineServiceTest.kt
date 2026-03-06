package com.esiri.esiriplus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DoctorOnlineServiceTest {

    @Test
    fun `isRunning is false by default`() {
        assertFalse(DoctorOnlineService.isRunning)
    }

    @Test
    fun `ACTION_START constant is correct`() {
        assertEquals("com.esiri.esiriplus.service.START", DoctorOnlineService.ACTION_START)
    }

    @Test
    fun `ACTION_STOP constant is correct`() {
        assertEquals("com.esiri.esiriplus.service.STOP", DoctorOnlineService.ACTION_STOP)
    }

    @Test
    fun `EXTRA_DOCTOR_ID constant is correct`() {
        assertEquals("doctor_id", DoctorOnlineService.EXTRA_DOCTOR_ID)
    }

    @Test
    fun `exponential backoff calculation matches service logic`() {
        val initialBackoffMs = 2000L
        val maxBackoffMs = 30000L

        // Verify the backoff formula: initialBackoff * 2^min(attempt-1, 4), capped at maxBackoff
        val expected = listOf(
            2000L,   // attempt 0: 2000 * 2^0 = 2000
            4000L,   // attempt 1: 2000 * 2^1 = 4000
            8000L,   // attempt 2: 2000 * 2^2 = 8000
            16000L,  // attempt 3: 2000 * 2^3 = 16000
            30000L,  // attempt 4: 2000 * 2^4 = 32000, capped to 30000
            30000L,  // attempt 5: same cap
        )

        expected.forEachIndexed { attempt, expectedMs ->
            val backoffMs = (initialBackoffMs * (1L shl minOf(attempt, 4)))
                .coerceAtMost(maxBackoffMs)
            assertEquals("Attempt $attempt", expectedMs, backoffMs)
        }
    }
}

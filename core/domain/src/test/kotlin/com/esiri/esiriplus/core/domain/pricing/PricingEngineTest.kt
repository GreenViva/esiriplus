package com.esiri.esiriplus.core.domain.pricing

import com.esiri.esiriplus.core.domain.model.ConsultationTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PricingEngineTest {

    // ── calculatePrice ────────────────────────────────────────────────────────

    @Test
    fun `Economy multiplier is 1 — price unchanged`() {
        assertEquals(10_000, PricingEngine.calculatePrice(10_000, ConsultationTier.ECONOMY))
    }

    @Test
    fun `Royal multiplier is 10 — price is 10x base`() {
        assertEquals(100_000, PricingEngine.calculatePrice(10_000, ConsultationTier.ROYAL))
    }

    @Test
    fun `calculatePrice handles zero base price`() {
        assertEquals(0, PricingEngine.calculatePrice(0, ConsultationTier.ROYAL))
        assertEquals(0, PricingEngine.calculatePrice(0, ConsultationTier.ECONOMY))
    }

    @Test
    fun `Nurse Economy price is 5000`() {
        assertEquals(5_000, PricingEngine.calculatePrice(5_000, ConsultationTier.ECONOMY))
    }

    @Test
    fun `Nurse Royal price is 50000`() {
        assertEquals(50_000, PricingEngine.calculatePrice(5_000, ConsultationTier.ROYAL))
    }

    @Test
    fun `GP Economy price is 10000`() {
        assertEquals(10_000, PricingEngine.calculatePrice(10_000, ConsultationTier.ECONOMY))
    }

    @Test
    fun `GP Royal price is 100000`() {
        assertEquals(100_000, PricingEngine.calculatePrice(10_000, ConsultationTier.ROYAL))
    }

    @Test
    fun `Specialist Royal price is 300000`() {
        assertEquals(300_000, PricingEngine.calculatePrice(30_000, ConsultationTier.ROYAL))
    }

    // ── getMultiplier ─────────────────────────────────────────────────────────

    @Test
    fun `Economy multiplier value is 1`() {
        assertEquals(1, PricingEngine.getMultiplier(ConsultationTier.ECONOMY))
    }

    @Test
    fun `Royal multiplier value is 10`() {
        assertEquals(10, PricingEngine.getMultiplier(ConsultationTier.ROYAL))
    }

    // ── getFollowUpWindowDays ─────────────────────────────────────────────────

    @Test
    fun `Royal follow-up window is 14 days`() {
        assertEquals(14, PricingEngine.getFollowUpWindowDays(ConsultationTier.ROYAL))
    }

    @Test
    fun `Economy follow-up window is 0 days (count-based)`() {
        assertEquals(0, PricingEngine.getFollowUpWindowDays(ConsultationTier.ECONOMY))
    }

    // ── getMaxFollowUps ───────────────────────────────────────────────────────

    @Test
    fun `Economy max follow-ups is 1`() {
        assertEquals(1, PricingEngine.getMaxFollowUps(ConsultationTier.ECONOMY))
    }

    @Test
    fun `Royal max follow-ups is unlimited (Int MAX_VALUE)`() {
        assertEquals(Int.MAX_VALUE, PricingEngine.getMaxFollowUps(ConsultationTier.ROYAL))
    }

    // ── calculateFollowUpExpiry ───────────────────────────────────────────────

    @Test
    fun `Royal follow-up expiry is 14 days after consultation end`() {
        val consultationEnd = 1_000_000_000L // arbitrary epoch millis
        val expected = consultationEnd + 14L * 24L * 60L * 60L * 1_000L
        assertEquals(expected, PricingEngine.calculateFollowUpExpiry(consultationEnd, ConsultationTier.ROYAL))
    }

    @Test
    fun `Economy follow-up expiry is null`() {
        assertNull(PricingEngine.calculateFollowUpExpiry(1_000_000_000L, ConsultationTier.ECONOMY))
    }

    // ── ConsultationTier.fromString ───────────────────────────────────────────

    @Test
    fun `fromString parses ROYAL case-insensitive`() {
        assertEquals(ConsultationTier.ROYAL, ConsultationTier.fromString("ROYAL"))
        assertEquals(ConsultationTier.ROYAL, ConsultationTier.fromString("royal"))
        assertEquals(ConsultationTier.ROYAL, ConsultationTier.fromString("Royal"))
    }

    @Test
    fun `fromString defaults to ECONOMY for unknown values`() {
        assertEquals(ConsultationTier.ECONOMY, ConsultationTier.fromString("unknown"))
        assertEquals(ConsultationTier.ECONOMY, ConsultationTier.fromString(""))
        assertEquals(ConsultationTier.ECONOMY, ConsultationTier.fromString("PREMIUM"))
    }
}

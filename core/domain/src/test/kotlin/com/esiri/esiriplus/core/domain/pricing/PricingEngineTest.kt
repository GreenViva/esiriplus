package com.esiri.esiriplus.core.domain.pricing

import com.esiri.esiriplus.core.domain.model.ConsultationTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PricingEngineTest {

    // ── calculatePrice — picks per-tier price (no multiplier) ─────────────────

    @Test
    fun `Economy picks basePrice`() {
        assertEquals(
            10_000,
            PricingEngine.calculatePrice(basePrice = 10_000, royalPrice = 420_000, ConsultationTier.ECONOMY),
        )
    }

    @Test
    fun `Royal picks royalPrice (not basePrice × 10)`() {
        // GP: Economy 10,000 / Royal 420,000 — explicit, not multiplied.
        assertEquals(
            420_000,
            PricingEngine.calculatePrice(basePrice = 10_000, royalPrice = 420_000, ConsultationTier.ROYAL),
        )
    }

    @Test
    fun `Nurse Economy is 3000`() {
        assertEquals(
            3_000,
            PricingEngine.calculatePrice(basePrice = 3_000, royalPrice = 322_000, ConsultationTier.ECONOMY),
        )
    }

    @Test
    fun `Nurse Royal is 322000 (was 30,000 under old 10× rule)`() {
        assertEquals(
            322_000,
            PricingEngine.calculatePrice(basePrice = 3_000, royalPrice = 322_000, ConsultationTier.ROYAL),
        )
    }

    @Test
    fun `Specialist Royal is 700000 (was 300,000 under old 10× rule)`() {
        assertEquals(
            700_000,
            PricingEngine.calculatePrice(basePrice = 30_000, royalPrice = 700_000, ConsultationTier.ROYAL),
        )
    }

    @Test
    fun `Psychologist Royal is 980000 (was 500,000 under old 10× rule)`() {
        assertEquals(
            980_000,
            PricingEngine.calculatePrice(basePrice = 50_000, royalPrice = 980_000, ConsultationTier.ROYAL),
        )
    }

    @Test
    fun `Zero prices are returned as-is`() {
        assertEquals(0, PricingEngine.calculatePrice(0, 0, ConsultationTier.ROYAL))
        assertEquals(0, PricingEngine.calculatePrice(0, 0, ConsultationTier.ECONOMY))
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
        val consultationEnd = 1_000_000_000L
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

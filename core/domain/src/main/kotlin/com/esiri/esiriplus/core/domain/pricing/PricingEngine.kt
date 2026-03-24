package com.esiri.esiriplus.core.domain.pricing

import com.esiri.esiriplus.core.domain.model.ConsultationTier

/**
 * Centralised, testable pricing engine for the tiered consultation system.
 *
 * Business rules:
 *  - Economy → base price × 1  (no change)
 *  - Royal   → base price × 10 (premium tier)
 *  - Royal provides a 14-day follow-up window; Economy allows 1 follow-up.
 *
 * Pricing is NEVER hardcoded in the UI. All price calculations flow through here.
 */
object PricingEngine {

    private const val ROYAL_MULTIPLIER = 10
    private const val ECONOMY_MULTIPLIER = 1

    /** Days after consultation end during which Royal patients may request follow-ups. */
    const val ROYAL_FOLLOW_UP_DAYS = 14

    /** Maximum follow-ups allowed for Economy patients. */
    const val ECONOMY_MAX_FOLLOW_UPS = 1

    /** Returns the multiplier for the given tier — keep this for display purposes. */
    fun getMultiplier(tier: ConsultationTier): Int = when (tier) {
        ConsultationTier.ROYAL -> ROYAL_MULTIPLIER
        ConsultationTier.ECONOMY -> ECONOMY_MULTIPLIER
    }

    /**
     * Calculates the effective price a patient pays for [basePrice] at the given [tier].
     *
     * Example:
     *   calculatePrice(10_000, ROYAL)   → 100_000
     *   calculatePrice(10_000, ECONOMY) → 10_000
     */
    fun calculatePrice(basePrice: Int, tier: ConsultationTier): Int =
        basePrice * getMultiplier(tier)

    /**
     * Returns the follow-up window in days for [tier].
     * ECONOMY returns 0 — follow-ups are tracked by count, not by a window.
     */
    fun getFollowUpWindowDays(tier: ConsultationTier): Int = when (tier) {
        ConsultationTier.ROYAL -> ROYAL_FOLLOW_UP_DAYS
        ConsultationTier.ECONOMY -> 0
    }

    /**
     * Returns the maximum number of follow-ups allowed for [tier] within one consultation.
     * Royal is unlimited within the 14-day window.
     */
    fun getMaxFollowUps(tier: ConsultationTier): Int = when (tier) {
        ConsultationTier.ROYAL -> Int.MAX_VALUE
        ConsultationTier.ECONOMY -> ECONOMY_MAX_FOLLOW_UPS
    }

    /**
     * Calculates the follow-up expiry timestamp for a Royal consultation.
     * Returns null for Economy (follow-up is count-based, not time-based).
     *
     * @param consultationEndMillis  epoch-millis when the consultation ended
     */
    fun calculateFollowUpExpiry(consultationEndMillis: Long, tier: ConsultationTier): Long? {
        if (tier != ConsultationTier.ROYAL) return null
        val windowMs = ROYAL_FOLLOW_UP_DAYS.toLong() * 24L * 60L * 60L * 1_000L
        return consultationEndMillis + windowMs
    }
}

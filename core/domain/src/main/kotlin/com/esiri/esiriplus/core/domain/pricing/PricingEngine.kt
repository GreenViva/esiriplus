package com.esiri.esiriplus.core.domain.pricing

import com.esiri.esiriplus.core.domain.model.ConsultationTier

/**
 * Centralised, testable pricing engine for the tiered consultation system.
 *
 * Business rules (since 2026-05-01):
 *  - Economy → uses [basePrice] (the per-service Economy price)
 *  - Royal   → uses [royalPrice] (the per-service Royal price, set explicitly)
 *  - Royal provides a 14-day follow-up window; Economy allows 1 follow-up.
 *
 * The 10× multiplier model was dropped — Royal prices are now explicit per
 * service in [com.esiri.esiriplus.core.database.entity.ServiceTierEntity] and
 * mirrored in `app_config.royal_price_<service>` server-side.
 */
object PricingEngine {

    /** Days after consultation end during which Royal patients may request follow-ups. */
    const val ROYAL_FOLLOW_UP_DAYS = 14

    /** Maximum follow-ups allowed for Economy patients. */
    const val ECONOMY_MAX_FOLLOW_UPS = 1

    /**
     * Picks the right price for [tier] given a service's per-tier prices.
     *
     * Example:
     *   calculatePrice(basePrice = 10_000, royalPrice = 420_000, ROYAL)   → 420_000
     *   calculatePrice(basePrice = 10_000, royalPrice = 420_000, ECONOMY) → 10_000
     */
    fun calculatePrice(basePrice: Int, royalPrice: Int, tier: ConsultationTier): Int = when (tier) {
        ConsultationTier.ROYAL -> royalPrice
        ConsultationTier.ECONOMY -> basePrice
    }

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

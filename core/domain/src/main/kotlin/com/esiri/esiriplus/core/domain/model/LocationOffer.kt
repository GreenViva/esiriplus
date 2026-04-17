package com.esiri.esiriplus.core.domain.model

/**
 * A location-based discount an admin has created for a specific district (and
 * optionally ward). The patient UI uses this to preview discounts on the
 * services screen before booking. Final discount is re-applied server-side
 * when the consultation is accepted, so tampering with this value client-side
 * does not actually grant a cheaper consultation.
 */
data class LocationOffer(
    val offerId: String,
    val title: String,
    val description: String?,
    val district: String,
    val ward: String?,
    /** Empty = applies to all service types. */
    val serviceTypes: List<String>,
    /** Empty = applies to all tiers. */
    val tiers: List<String>,
    val discountType: DiscountType,
    /** Percent (1–100) or fixed TZS — ignored when [discountType] is FREE. */
    val discountValue: Int,
) {
    enum class DiscountType { FREE, PERCENT, FIXED }

    /** Returns true if [serviceCategory] (e.g. "NURSE", "nurse") is covered by this offer. */
    fun appliesTo(serviceCategory: String): Boolean {
        if (serviceTypes.isEmpty()) return true
        val normalized = serviceCategory.lowercase()
        return serviceTypes.any { it.lowercase() == normalized }
    }

    /** Applies this offer's discount to [basePrice] (already tier-adjusted). */
    fun applyTo(basePrice: Int): Int = when (discountType) {
        DiscountType.FREE -> 0
        DiscountType.PERCENT -> {
            val pct = discountValue.coerceIn(0, 100)
            ((basePrice.toLong() * (100 - pct)) / 100).toInt().coerceAtLeast(0)
        }
        DiscountType.FIXED -> (basePrice - discountValue).coerceAtLeast(0)
    }

    /** Human-readable summary for UI chips/badges. */
    fun shortLabel(): String = when (discountType) {
        DiscountType.FREE -> "FREE"
        DiscountType.PERCENT -> "$discountValue% OFF"
        DiscountType.FIXED -> "TZS ${"%,d".format(discountValue)} OFF"
    }
}

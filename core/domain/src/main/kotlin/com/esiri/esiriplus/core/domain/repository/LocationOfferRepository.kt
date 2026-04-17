package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.LocationOffer

interface LocationOfferRepository {
    /**
     * Fetches every currently-active offer the authenticated patient can still
     * redeem for the given [district]/[ward]/[tier] combination. Returns an
     * empty list when [district] is null/blank or when nothing matches —
     * callers should treat empty as "no discount to preview".
     */
    suspend fun fetchApplicableOffers(
        district: String?,
        ward: String?,
        tier: String,
    ): Result<List<LocationOffer>>
}

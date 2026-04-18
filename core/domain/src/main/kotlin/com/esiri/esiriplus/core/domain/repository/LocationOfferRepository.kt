package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.LocationOffer

interface LocationOfferRepository {
    /**
     * Fetches every currently-active offer the authenticated patient can still
     * redeem for the given location + [tier] combination. The backend infers
     * missing ancestors, so sending any one of region/district/ward/street is
     * enough to match offers at any broader level.
     */
    suspend fun fetchApplicableOffers(
        region: String?,
        district: String?,
        ward: String?,
        street: String?,
        tier: String,
    ): Result<List<LocationOffer>>
}

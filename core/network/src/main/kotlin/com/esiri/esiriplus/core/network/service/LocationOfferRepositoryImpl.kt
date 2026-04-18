package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.LocationOffer
import com.esiri.esiriplus.core.domain.repository.LocationOfferRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class CheckOfferResponse(
    val offers: List<OfferDto> = emptyList(),
)

@Serializable
private data class OfferDto(
    @SerialName("offer_id") val offerId: String,
    val title: String,
    val description: String? = null,
    val region: String? = null,
    val district: String? = null,
    val ward: String? = null,
    val street: String? = null,
    @SerialName("service_types") val serviceTypes: List<String> = emptyList(),
    val tiers: List<String> = emptyList(),
    @SerialName("discount_type") val discountType: String,
    @SerialName("discount_value") val discountValue: Int = 0,
)

@Singleton
class LocationOfferRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : LocationOfferRepository {

    override suspend fun fetchApplicableOffers(
        region: String?,
        district: String?,
        ward: String?,
        street: String?,
        tier: String,
    ): Result<List<LocationOffer>> {
        if (region.isNullOrBlank() && district.isNullOrBlank() &&
            ward.isNullOrBlank() && street.isNullOrBlank()
        ) {
            return Result.Success(emptyList())
        }

        val body = buildJsonObject {
            if (!region.isNullOrBlank())   put("service_region", region)
            if (!district.isNullOrBlank()) put("service_district", district)
            if (!ward.isNullOrBlank())     put("service_ward", ward)
            if (!street.isNullOrBlank())   put("service_street", street)
            put("service_tier", tier.uppercase())
        }

        return when (val result = edgeFunctionClient.invokeAndDecode<CheckOfferResponse>(
            functionName = "check-location-offer",
            body = body,
            patientAuth = true,
        )) {
            is ApiResult.Success ->
                Result.Success(result.data.offers.map { it.toDomain() })
            is ApiResult.Error -> {
                Log.w(TAG, "Offer fetch failed: ${result.message}")
                // Degrade gracefully — no offers beats a broken UI
                Result.Success(emptyList())
            }
            is ApiResult.NetworkError -> {
                Log.w(TAG, "Offer fetch network error: ${result.message}")
                Result.Success(emptyList())
            }
            is ApiResult.Unauthorized -> Result.Error(
                Exception("Unauthorized"),
                "Session expired",
            )
        }
    }

    private fun OfferDto.toDomain(): LocationOffer {
        val type = when (discountType.lowercase()) {
            "free"    -> LocationOffer.DiscountType.FREE
            "percent" -> LocationOffer.DiscountType.PERCENT
            "fixed"   -> LocationOffer.DiscountType.FIXED
            else      -> LocationOffer.DiscountType.PERCENT
        }
        return LocationOffer(
            offerId = offerId,
            title = title,
            description = description,
            region = region,
            district = district,
            ward = ward,
            street = street,
            serviceTypes = serviceTypes,
            tiers = tiers,
            discountType = type,
            discountValue = discountValue,
        )
    }

    companion object {
        private const val TAG = "LocationOfferRepo"
    }
}

package com.esiri.esiriplus.core.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * One row from the `list_missed_for_patient` PostgREST RPC. Each row stands
 * for a paid-but-unresolved consultation that should appear on the patient's
 * Missed dashboard tile.
 *
 * `sourceKind` is the discriminator that tells the reconnect flow which
 * source table to mark consumed when the patient retries:
 *  - "request_expired"   → consultation_requests
 *  - "no_engagement"     → consultations
 *  - "paid_no_request"   → service_access_payments
 */
@JsonClass(generateAdapter = true)
data class MissedConsultationApiModel(
    @Json(name = "source_kind") val sourceKind: String,
    @Json(name = "source_id") val sourceId: String,
    @Json(name = "service_type") val serviceType: String,
    @Json(name = "service_tier") val serviceTier: String,
    @Json(name = "consultation_fee") val consultationFee: Int = 0,
    @Json(name = "doctor_id") val doctorId: String? = null,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class ListMissedRpcBody(
    @Json(name = "p_session_id") val sessionId: String,
)

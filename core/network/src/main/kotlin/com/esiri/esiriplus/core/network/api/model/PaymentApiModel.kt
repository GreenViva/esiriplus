package com.esiri.esiriplus.core.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentApiModel(
    @Json(name = "payment_id") val paymentId: String,
    @Json(name = "patient_session_id") val patientSessionId: String? = null,
    val amount: Int,
    val currency: String = "TZS",
    val status: String,
    @Json(name = "transaction_id") val transactionId: String? = null,
    @Json(name = "failure_reason") val failureReason: String? = null,
    @Json(name = "created_at") val createdAt: String,
)

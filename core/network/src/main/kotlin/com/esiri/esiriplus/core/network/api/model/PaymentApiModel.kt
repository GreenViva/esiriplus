package com.esiri.esiriplus.core.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaymentApiModel(
    val id: String,
    @Json(name = "consultation_id") val consultationId: String,
    val amount: Int,
    val currency: String = "KES",
    val status: String,
    @Json(name = "mpesa_receipt_number") val mpesaReceiptNumber: String? = null,
    @Json(name = "created_at") val createdAt: String,
)

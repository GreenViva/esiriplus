package com.esiri.esiriplus.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InitiatePaymentRequest(
    @SerialName("consultation_id") val consultationId: String,
    val phone: String,
    val amount: Int,
    @SerialName("idempotency_key") val idempotencyKey: String,
)

@Serializable
data class PaymentResponse(
    val id: String,
    @SerialName("consultation_id") val consultationId: String,
    val amount: Int,
    val currency: String = "KES",
    val status: String,
    @SerialName("mpesa_receipt_number") val mpesaReceiptNumber: String? = null,
    @SerialName("created_at") val createdAt: String,
)

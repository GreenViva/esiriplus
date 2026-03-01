package com.esiri.esiriplus.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request body for mpesa-stk-push edge function. */
@Serializable
data class StkPushRequest(
    @SerialName("phone_number") val phoneNumber: String,
    val amount: Int,
    @SerialName("consultation_id") val consultationId: String? = null,
    @SerialName("payment_type") val paymentType: String,
    @SerialName("service_type") val serviceType: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String,
)

/** Response from mpesa-stk-push edge function. */
@Serializable
data class StkPushResponse(
    val message: String,
    @SerialName("payment_id") val paymentId: String,
    @SerialName("checkout_request_id") val checkoutRequestId: String,
    @SerialName("payment_env") val paymentEnv: String? = null,
    val status: String? = null,
)

/** Response when querying a payment by ID via PostgREST. */
@Serializable
data class PaymentStatusResponse(
    @SerialName("payment_id") val paymentId: String,
    val status: String,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
)

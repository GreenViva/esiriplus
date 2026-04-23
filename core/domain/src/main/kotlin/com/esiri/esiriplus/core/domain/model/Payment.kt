package com.esiri.esiriplus.core.domain.model

data class Payment(
    val paymentId: String,
    val patientSessionId: String,
    val amount: Int,
    val paymentMethod: PaymentMethod,
    val transactionId: String? = null,
    val phoneNumber: String,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val failureReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val synced: Boolean = false,
)

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class PaymentMethod {
    /** Classic M-Pesa STK push to the SIM. Existing flow. */
    MPESA,

    /**
     * User enters a mobile number, receives a push notification on their
     * device, and confirms the payment by entering a one-time PIN. Backed by
     * the initiate-mobile-payment / confirm-mobile-payment edge functions.
     * Provider integration is a TODO — see docs/mobile-payment-architecture.md.
     */
    MOBILE_NUMBER,
}

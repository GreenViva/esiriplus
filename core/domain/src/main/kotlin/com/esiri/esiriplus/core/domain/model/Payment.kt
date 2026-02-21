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
    MPESA,
}

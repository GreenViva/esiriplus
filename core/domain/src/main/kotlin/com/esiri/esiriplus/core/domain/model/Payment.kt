package com.esiri.esiriplus.core.domain.model

import java.time.Instant

data class Payment(
    val id: String,
    val consultationId: String,
    val amount: Int,
    val currency: String = "KES",
    val status: PaymentStatus = PaymentStatus.PENDING,
    val mpesaReceiptNumber: String? = null,
    val createdAt: Instant,
)

enum class PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED,
}

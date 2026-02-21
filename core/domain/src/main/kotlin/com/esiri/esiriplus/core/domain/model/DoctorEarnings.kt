package com.esiri.esiriplus.core.domain.model

data class DoctorEarnings(
    val earningId: String,
    val doctorId: String,
    val consultationId: String,
    val amount: Int,
    val status: EarningStatus,
    val paidAt: Long? = null,
    val createdAt: Long,
)

enum class EarningStatus {
    PENDING,
    PAID,
}

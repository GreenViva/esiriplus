package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Payment
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun getPaymentsForConsultation(consultationId: String): Flow<List<Payment>>
    suspend fun initiatePayment(consultationId: String, phone: String, amount: Int): Result<Payment>
    suspend fun checkPaymentStatus(paymentId: String): Result<Payment>
}

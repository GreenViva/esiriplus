package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Payment
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun getPaymentsBySession(sessionId: String): Flow<List<Payment>>
    fun getPaymentsByStatus(status: String): Flow<List<Payment>>
    fun getTransactionHistory(limit: Int = 20, offset: Int = 0): Flow<List<Payment>>
    suspend fun getPaymentById(paymentId: String): Payment?
    suspend fun createPayment(payment: Payment): Result<Payment>
    suspend fun updatePaymentStatus(paymentId: String, status: String, transactionId: String? = null)
    suspend fun getUnsyncedPayments(): List<Payment>
    suspend fun markPaymentSynced(paymentId: String)
    suspend fun clearAll()
}

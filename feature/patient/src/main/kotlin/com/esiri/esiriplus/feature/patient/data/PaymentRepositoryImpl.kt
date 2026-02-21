package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Payment
import com.esiri.esiriplus.core.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepositoryImpl @Inject constructor() : PaymentRepository {

    override fun getPaymentsBySession(sessionId: String): Flow<List<Payment>> {
        TODO("Mock — API not yet available")
    }

    override fun getPaymentsByStatus(status: String): Flow<List<Payment>> {
        TODO("Mock — API not yet available")
    }

    override fun getTransactionHistory(limit: Int, offset: Int): Flow<List<Payment>> {
        TODO("Mock — API not yet available")
    }

    override suspend fun getPaymentById(paymentId: String): Payment? {
        TODO("Mock — API not yet available")
    }

    override suspend fun createPayment(payment: Payment): Result<Payment> {
        TODO("Mock — API not yet available")
    }

    override suspend fun updatePaymentStatus(paymentId: String, status: String, transactionId: String?) {
        TODO("Mock — API not yet available")
    }

    override suspend fun getUnsyncedPayments(): List<Payment> {
        TODO("Mock — API not yet available")
    }

    override suspend fun markPaymentSynced(paymentId: String) {
        TODO("Mock — API not yet available")
    }

    override suspend fun clearAll() {
        TODO("Mock — API not yet available")
    }
}

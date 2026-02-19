package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Payment
import com.esiri.esiriplus.core.domain.repository.PaymentRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UnusedPrivateProperty")
@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : PaymentRepository {

    override fun getPaymentsForConsultation(consultationId: String): Flow<List<Payment>> =
        flowOf(emptyList()) // TODO: Implement

    override suspend fun initiatePayment(consultationId: String, phone: String, amount: Int): Result<Payment> =
        Result.Error(NotImplementedError("Not yet implemented"))

    override suspend fun checkPaymentStatus(paymentId: String): Result<Payment> =
        Result.Error(NotImplementedError("Not yet implemented"))
}

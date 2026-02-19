package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Payment
import com.esiri.esiriplus.core.domain.repository.PaymentRepository
import javax.inject.Inject

class InitiatePaymentUseCase @Inject constructor(
    private val paymentRepository: PaymentRepository,
) {
    suspend operator fun invoke(consultationId: String, phone: String, amount: Int): Result<Payment> =
        paymentRepository.initiatePayment(consultationId, phone, amount)
}

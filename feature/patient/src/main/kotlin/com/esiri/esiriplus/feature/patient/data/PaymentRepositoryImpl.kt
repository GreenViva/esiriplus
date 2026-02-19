package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.IdempotencyKeyGenerator
import com.esiri.esiriplus.core.domain.model.Payment
import com.esiri.esiriplus.core.domain.repository.PaymentRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.api.SupabaseApi
import com.esiri.esiriplus.core.network.api.model.toDomain
import com.esiri.esiriplus.core.network.dto.InitiatePaymentRequest
import com.esiri.esiriplus.core.network.dto.PaymentResponse
import com.esiri.esiriplus.core.network.model.safeApiCall
import com.esiri.esiriplus.core.network.model.toDomainResult
import com.esiri.esiriplus.core.network.model.toApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val supabaseApi: SupabaseApi,
    private val edgeFunctionClient: EdgeFunctionClient,
) : PaymentRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getPaymentsForConsultation(consultationId: String): Flow<List<Payment>> = flow {
        val response = supabaseApi.getPaymentsForConsultation(
            consultationIdFilter = "eq.$consultationId",
        )
        val result = response.toApiResult()
        emit(result.getOrNull()?.map { it.toDomain() } ?: emptyList())
    }

    override suspend fun initiatePayment(
        consultationId: String,
        phone: String,
        amount: Int,
    ): Result<Payment> {
        val request = InitiatePaymentRequest(
            consultationId = consultationId,
            phone = phone,
            amount = amount,
            idempotencyKey = IdempotencyKeyGenerator.generate("payment"),
        )
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invokeAndDecode<PaymentResponse>(
            functionName = "initiate-payment",
            body = body,
        )

        return apiResult.map { response ->
            Payment(
                id = response.id,
                consultationId = response.consultationId,
                amount = response.amount,
                currency = response.currency,
                status = com.esiri.esiriplus.core.domain.model.PaymentStatus.entries
                    .find { it.name.equals(response.status, ignoreCase = true) }
                    ?: com.esiri.esiriplus.core.domain.model.PaymentStatus.PENDING,
                mpesaReceiptNumber = response.mpesaReceiptNumber,
                createdAt = Instant.parse(response.createdAt),
            )
        }.toDomainResult()
    }

    override suspend fun checkPaymentStatus(paymentId: String): Result<Payment> {
        val apiResult = safeApiCall {
            val response = supabaseApi.getPayment(idFilter = "eq.$paymentId")
            response.toApiResult().getOrThrow().toDomain()
        }
        return apiResult.toDomainResult()
    }
}

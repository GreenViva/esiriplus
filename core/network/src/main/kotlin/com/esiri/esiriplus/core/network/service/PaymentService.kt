package com.esiri.esiriplus.core.network.service

import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.api.SupabaseApi
import com.esiri.esiriplus.core.network.api.model.PaymentApiModel
import com.esiri.esiriplus.core.network.dto.InitiateMobilePaymentResponse
import com.esiri.esiriplus.core.network.dto.StkPushResponse
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentService @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
    private val supabaseApi: SupabaseApi,
) {
    /**
     * Initiate an M-Pesa STK push for service access (nurse, GP, specialist, etc.).
     */
    suspend fun initiateServicePayment(
        phoneNumber: String,
        amount: Int,
        serviceType: String,
        consultationId: String? = null,
        idempotencyKey: String,
    ): ApiResult<StkPushResponse> {
        val body = buildJsonObject {
            put("phone_number", phoneNumber)
            put("amount", amount)
            put("payment_type", "service_access")
            put("service_type", serviceType)
            put("idempotency_key", idempotencyKey)
            if (consultationId != null) put("consultation_id", consultationId)
        }
        return edgeFunctionClient.invokeAndDecode("mpesa-stk-push", body)
    }

    /**
     * Initiate an M-Pesa STK push for call recharge.
     */
    suspend fun initiateCallRecharge(
        phoneNumber: String,
        amount: Int,
        consultationId: String,
        idempotencyKey: String,
    ): ApiResult<StkPushResponse> {
        val body = buildJsonObject {
            put("phone_number", phoneNumber)
            put("amount", amount)
            put("payment_type", "call_recharge")
            put("consultation_id", consultationId)
            put("idempotency_key", idempotencyKey)
        }
        return edgeFunctionClient.invokeAndDecode("mpesa-stk-push", body)
    }

    /**
     * "Pay by Mobile Number" — provider-driven flow. Creates a payments row
     * and asks the mobile-money provider to push its own wallet prompt to the
     * user's device. The user enters their wallet PIN on the provider's UI;
     * we never see it. The client polls [getPaymentStatus] afterwards, same
     * as the STK path. See docs/mobile-payment-architecture.md.
     */
    suspend fun initiateMobilePayment(
        phoneNumber: String,
        amount: Int,
        paymentType: String,
        serviceType: String? = null,
        consultationId: String? = null,
        idempotencyKey: String,
    ): ApiResult<InitiateMobilePaymentResponse> {
        val body = buildJsonObject {
            put("phone_number", phoneNumber)
            put("amount", amount)
            put("payment_type", paymentType)
            if (serviceType != null) put("service_type", serviceType)
            if (consultationId != null) put("consultation_id", consultationId)
            put("idempotency_key", idempotencyKey)
        }
        return edgeFunctionClient.invokeAndDecode("initiate-mobile-payment", body)
    }

    /**
     * Poll payment status from the backend via PostgREST.
     */
    suspend fun getPaymentStatus(paymentId: String): ApiResult<PaymentApiModel> {
        return try {
            val response = supabaseApi.getPayment(paymentIdFilter = "eq.$paymentId")
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(404, "Payment not found")
                }
            } else {
                ApiResult.Error(response.code(), response.errorBody()?.string() ?: "Unknown error")
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e, e.message ?: "Network error")
        }
    }
}

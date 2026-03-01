package com.esiri.esiriplus.core.network.service

import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ConsultationSyncResponse(
    @SerialName("consultation_id") val consultationId: String,
    val status: String,
    @SerialName("service_type") val serviceType: String,
    @SerialName("consultation_fee") val consultationFee: Int,
    @SerialName("scheduled_end_at") val scheduledEndAt: String? = null,
    @SerialName("extension_count") val extensionCount: Int = 0,
    @SerialName("grace_period_end_at") val gracePeriodEndAt: String? = null,
    @SerialName("original_duration_minutes") val originalDurationMinutes: Int = 15,
    @SerialName("session_start_time") val sessionStartTime: String? = null,
    @SerialName("server_time") val serverTime: String,
)

@Singleton
class ConsultationTimerService @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) {
    private val functionName = "manage-consultation"

    suspend fun sync(consultationId: String): ApiResult<ConsultationSyncResponse> {
        return edgeFunctionClient.invokeAndDecode(
            functionName = functionName,
            body = buildJsonObject {
                put("action", "sync")
                put("consultation_id", consultationId)
            },
        )
    }

    suspend fun endConsultation(consultationId: String): ApiResult<ConsultationSyncResponse> {
        return edgeFunctionClient.invokeAndDecode(
            functionName = functionName,
            body = buildJsonObject {
                put("action", "end")
                put("consultation_id", consultationId)
            },
        )
    }

    suspend fun timerExpired(consultationId: String): ApiResult<ConsultationSyncResponse> {
        return edgeFunctionClient.invokeAndDecode(
            functionName = functionName,
            body = buildJsonObject {
                put("action", "timer_expired")
                put("consultation_id", consultationId)
            },
        )
    }

    suspend fun requestExtension(consultationId: String): ApiResult<ConsultationSyncResponse> {
        return edgeFunctionClient.invokeAndDecode(
            functionName = functionName,
            body = buildJsonObject {
                put("action", "request_extension")
                put("consultation_id", consultationId)
            },
        )
    }

    suspend fun acceptExtension(consultationId: String): ApiResult<ConsultationSyncResponse> {
        return edgeFunctionClient.invokeAndDecode(
            functionName = functionName,
            body = buildJsonObject {
                put("action", "accept_extension")
                put("consultation_id", consultationId)
            },
        )
    }

    suspend fun declineExtension(consultationId: String): ApiResult<ConsultationSyncResponse> {
        return edgeFunctionClient.invokeAndDecode(
            functionName = functionName,
            body = buildJsonObject {
                put("action", "decline_extension")
                put("consultation_id", consultationId)
            },
        )
    }

    suspend fun paymentConfirmed(
        consultationId: String,
        paymentId: String,
    ): ApiResult<ConsultationSyncResponse> {
        return edgeFunctionClient.invokeAndDecode(
            functionName = functionName,
            body = buildJsonObject {
                put("action", "payment_confirmed")
                put("consultation_id", consultationId)
                put("payment_id", paymentId)
            },
        )
    }

    suspend fun cancelPayment(consultationId: String): ApiResult<ConsultationSyncResponse> {
        return edgeFunctionClient.invokeAndDecode(
            functionName = functionName,
            body = buildJsonObject {
                put("action", "cancel_payment")
                put("consultation_id", consultationId)
            },
        )
    }
}

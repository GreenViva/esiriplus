package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ConsultationRequestRow(
    @SerialName("request_id") val requestId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("ttl_seconds") val ttlSeconds: Int? = null,
    @SerialName("consultation_id") val consultationId: String? = null,
)

@Singleton
class ConsultationRequestService @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) {

    suspend fun createRequest(
        doctorId: String,
        serviceType: String,
        consultationType: String,
        chiefComplaint: String,
    ): ApiResult<ConsultationRequestRow> {
        val body = buildJsonObject {
            put("action", "create")
            put("doctor_id", doctorId)
            put("service_type", serviceType)
            put("consultation_type", consultationType)
            put("chief_complaint", chiefComplaint)
        }
        return safeApiCall {
            val raw = edgeFunctionClient.invoke(FUNCTION_NAME, body).getOrThrow()
            edgeFunctionClient.json.decodeFromString<ConsultationRequestRow>(raw)
        }
    }

    suspend fun acceptRequest(requestId: String): ApiResult<ConsultationRequestRow> {
        val body = buildJsonObject {
            put("action", "accept")
            put("request_id", requestId)
        }
        return safeApiCall {
            val raw = edgeFunctionClient.invoke(FUNCTION_NAME, body).getOrThrow()
            Log.d(TAG, "Accept response: $raw")
            edgeFunctionClient.json.decodeFromString<ConsultationRequestRow>(raw)
        }
    }

    suspend fun rejectRequest(requestId: String): ApiResult<ConsultationRequestRow> {
        val body = buildJsonObject {
            put("action", "reject")
            put("request_id", requestId)
        }
        return safeApiCall {
            val raw = edgeFunctionClient.invoke(FUNCTION_NAME, body).getOrThrow()
            edgeFunctionClient.json.decodeFromString<ConsultationRequestRow>(raw)
        }
    }

    suspend fun expireRequest(requestId: String): ApiResult<ConsultationRequestRow> {
        val body = buildJsonObject {
            put("action", "expire")
            put("request_id", requestId)
        }
        return safeApiCall {
            val raw = edgeFunctionClient.invoke(FUNCTION_NAME, body).getOrThrow()
            edgeFunctionClient.json.decodeFromString<ConsultationRequestRow>(raw)
        }
    }

    companion object {
        private const val TAG = "ConsultReqService"
        private const val FUNCTION_NAME = "handle-consultation-request"
    }
}

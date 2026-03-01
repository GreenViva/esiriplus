package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
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
    @SerialName("debug_error") val debugError: String? = null,
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
        symptoms: String? = null,
        patientAgeGroup: String? = null,
        patientSex: String? = null,
        patientBloodGroup: String? = null,
        patientAllergies: String? = null,
        patientChronicConditions: String? = null,
    ): ApiResult<ConsultationRequestRow> {
        val body = buildJsonObject {
            put("action", "create")
            put("doctor_id", doctorId)
            put("service_type", serviceType)
            put("consultation_type", consultationType)
            put("chief_complaint", chiefComplaint)
            if (!symptoms.isNullOrBlank()) put("symptoms", symptoms)
            if (!patientAgeGroup.isNullOrBlank()) put("patient_age_group", patientAgeGroup)
            if (!patientSex.isNullOrBlank()) put("patient_sex", patientSex)
            if (!patientBloodGroup.isNullOrBlank()) put("patient_blood_group", patientBloodGroup)
            if (!patientAllergies.isNullOrBlank()) put("patient_allergies", patientAllergies)
            if (!patientChronicConditions.isNullOrBlank()) put("patient_chronic_conditions", patientChronicConditions)
        }
        return decodeEdgeFunctionResult(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun acceptRequest(requestId: String): ApiResult<ConsultationRequestRow> {
        val body = buildJsonObject {
            put("action", "accept")
            put("request_id", requestId)
        }
        Log.d(TAG, "Accept: calling edge function for request=$requestId")
        // edgeFunctionClient.invoke() already wraps in safeApiCall — do NOT double-wrap.
        // Instead, pattern-match the result and only parse on success.
        return when (val rawResult = edgeFunctionClient.invoke(FUNCTION_NAME, body)) {
            is ApiResult.Success -> {
                val raw = rawResult.data
                Log.d(TAG, "Accept response body: $raw")
                try {
                    val row = edgeFunctionClient.json.decodeFromString<ConsultationRequestRow>(raw)
                    // The edge function returns status="insert_error" when consultation creation fails.
                    if (row.status == "insert_error") {
                        val debugError = row.debugError ?: "Failed to create consultation"
                        Log.e(TAG, "Accept returned insert_error: $debugError")
                        ApiResult.Error(code = 409, message = debugError)
                    } else {
                        Log.d(TAG, "Accept SUCCESS: consultationId=${row.consultationId}")
                        ApiResult.Success(row)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Accept: failed to parse response", e)
                    ApiResult.NetworkError(e, "Failed to parse accept response: ${e.message}")
                }
            }
            is ApiResult.Error -> {
                Log.e(TAG, "Accept ERROR: code=${rawResult.code}, message=${rawResult.message}")
                rawResult
            }
            is ApiResult.NetworkError -> {
                Log.e(TAG, "Accept NETWORK ERROR: ${rawResult.message}", rawResult.exception)
                rawResult
            }
            is ApiResult.Unauthorized -> {
                Log.e(TAG, "Accept UNAUTHORIZED")
                rawResult
            }
        }
    }

    suspend fun rejectRequest(requestId: String): ApiResult<ConsultationRequestRow> {
        val body = buildJsonObject {
            put("action", "reject")
            put("request_id", requestId)
        }
        return decodeEdgeFunctionResult(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    suspend fun expireRequest(requestId: String): ApiResult<ConsultationRequestRow> {
        val body = buildJsonObject {
            put("action", "expire")
            put("request_id", requestId)
        }
        return decodeEdgeFunctionResult(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    /**
     * Poll the current status of a consultation request.
     * Used as a fallback when Realtime is unreliable.
     */
    suspend fun checkRequestStatus(requestId: String): ApiResult<ConsultationRequestRow> {
        val body = buildJsonObject {
            put("action", "status")
            put("request_id", requestId)
        }
        return decodeEdgeFunctionResult(edgeFunctionClient.invoke(FUNCTION_NAME, body))
    }

    /**
     * Safely decode an edge function result without double-wrapping in safeApiCall.
     * [EdgeFunctionClient.invoke] already wraps errors via safeApiCall; calling
     * .getOrThrow() inside another safeApiCall destroys the original error message.
     */
    private fun decodeEdgeFunctionResult(rawResult: ApiResult<String>): ApiResult<ConsultationRequestRow> {
        return when (rawResult) {
            is ApiResult.Success -> try {
                ApiResult.Success(edgeFunctionClient.json.decodeFromString<ConsultationRequestRow>(rawResult.data))
            } catch (e: Exception) {
                ApiResult.NetworkError(e, "Failed to parse response: ${e.message}")
            }
            is ApiResult.Error -> rawResult
            is ApiResult.NetworkError -> rawResult
            is ApiResult.Unauthorized -> rawResult
        }
    }

    companion object {
        private const val TAG = "ConsultReqService"
        private const val FUNCTION_NAME = "handle-consultation-request"
    }
}

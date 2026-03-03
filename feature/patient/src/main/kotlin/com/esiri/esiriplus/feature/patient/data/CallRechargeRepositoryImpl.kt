package com.esiri.esiriplus.feature.patient.data

import android.util.Log
import com.esiri.esiriplus.core.domain.repository.CallRechargeRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRechargeRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : CallRechargeRepository {

    override suspend fun submitRecharge(
        consultationId: String,
        minutes: Int,
        phoneNumber: String,
    ): Boolean {
        val body = buildJsonObject {
            put("consultation_id", consultationId)
            put("minutes", minutes)
            put("phone_number", phoneNumber)
            put("idempotency_key", UUID.randomUUID().toString())
        }
        return when (val result = edgeFunctionClient.invoke("call-recharge-payment", body, patientAuth = true)) {
            is ApiResult.Success -> {
                Log.d(TAG, "Call recharge initiated: $minutes min for consultation $consultationId")
                true
            }
            else -> {
                Log.e(TAG, "Call recharge failed: $result")
                false
            }
        }
    }

    companion object {
        private const val TAG = "CallRechargeRepo"
    }
}

package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class EarningsRow(
    @SerialName("earning_id") val earningId: String,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("consultation_id") val consultationId: String,
    val amount: Int,
    val status: String,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Singleton
class DoctorEarningsService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {

    suspend fun getEarningsForDoctor(doctorId: String): ApiResult<List<EarningsRow>> {
        return safeApiCall {
            val result = supabaseClientProvider.client.from("doctor_earnings")
                .select {
                    filter { eq("doctor_id", doctorId) }
                }
                .decodeList<EarningsRow>()
            Log.d(TAG, "Fetched ${result.size} earnings for doctor $doctorId")
            result
        }
    }

    companion object {
        private const val TAG = "DoctorEarningsSvc"
    }
}

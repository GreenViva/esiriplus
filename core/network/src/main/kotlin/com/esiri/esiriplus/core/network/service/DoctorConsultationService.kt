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
data class ConsultationRow(
    @SerialName("consultation_id") val consultationId: String,
    @SerialName("patient_session_id") val patientSessionId: String,
    @SerialName("doctor_id") val doctorId: String,
    val status: String,
    @SerialName("service_type") val serviceType: String = "general",
    @SerialName("consultation_fee") val consultationFee: Int = 0,
    @SerialName("session_start_time") val sessionStartTime: String? = null,
    @SerialName("session_end_time") val sessionEndTime: String? = null,
    @SerialName("session_duration_minutes") val sessionDurationMinutes: Int = 15,
    @SerialName("request_expires_at") val requestExpiresAt: String? = null,
    @SerialName("scheduled_end_at") val scheduledEndAt: String? = null,
    @SerialName("extension_count") val extensionCount: Int = 0,
    @SerialName("grace_period_end_at") val gracePeriodEndAt: String? = null,
    @SerialName("original_duration_minutes") val originalDurationMinutes: Int = 15,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Singleton
class DoctorConsultationService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {

    suspend fun getConsultationsForDoctor(doctorId: String): ApiResult<List<ConsultationRow>> {
        return safeApiCall {
            val result = supabaseClientProvider.client.from("consultations")
                .select {
                    filter { eq("doctor_id", doctorId) }
                }
                .decodeList<ConsultationRow>()
            Log.d(TAG, "Fetched ${result.size} consultations for doctor $doctorId")
            result
        }
    }

    companion object {
        private const val TAG = "DoctorConsultationSvc"
    }
}

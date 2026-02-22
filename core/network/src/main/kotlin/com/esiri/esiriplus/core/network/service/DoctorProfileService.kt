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
data class DoctorProfileRow(
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
    val phone: String,
    val specialty: String,
    @SerialName("specialist_field") val specialistField: String? = null,
    val languages: List<String> = emptyList(),
    val bio: String = "",
    @SerialName("license_number") val licenseNumber: String = "",
    @SerialName("years_experience") val yearsExperience: Int = 0,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
    @SerialName("average_rating") val averageRating: Double = 0.0,
    @SerialName("total_ratings") val totalRatings: Int = 0,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_available") val isAvailable: Boolean = false,
    val services: List<String> = emptyList(),
    @SerialName("country_code") val countryCode: String = "+255",
    val country: String = "",
    @SerialName("license_document_url") val licenseDocumentUrl: String? = null,
    @SerialName("certificates_url") val certificatesUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Singleton
class DoctorProfileService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {

    suspend fun getDoctorProfile(doctorId: String): ApiResult<DoctorProfileRow?> {
        return safeApiCall {
            val result = supabaseClientProvider.client.from("doctor_profiles")
                .select {
                    filter { eq("doctor_id", doctorId) }
                }
                .decodeSingleOrNull<DoctorProfileRow>()
            Log.d(TAG, "Fetched profile for doctor $doctorId: ${result != null}")
            result
        }
    }

    companion object {
        private const val TAG = "DoctorProfileSvc"
    }
}

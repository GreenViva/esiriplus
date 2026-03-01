package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Parse the `services` field which can be either:
 * - a JSON array: `["a","b"]`
 * - a stringified JSON array (text column): `"[\"a\",\"b\"]"`
 */
fun parseServicesField(raw: JsonElement?): List<String> {
    if (raw == null) return emptyList()
    return try {
        // Try as direct JSON array first
        raw.jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) {
        // It's a JSON string containing an array — unwrap and parse
        try {
            val str = raw.jsonPrimitive.content
            lenientJson.decodeFromString<List<String>>(str)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

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
    @SerialName("services") val servicesRaw: JsonElement? = null,
    @SerialName("country_code") val countryCode: String = "+255",
    val country: String = "",
    @SerialName("license_document_url") val licenseDocumentUrl: String? = null,
    @SerialName("certificates_url") val certificatesUrl: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("suspended_until") val suspendedUntil: String? = null,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("banned_at") val bannedAt: String? = null,
    @SerialName("ban_reason") val banReason: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    /** Parsed services list (handles both JSON array and stringified array from DB). */
    val services: List<String> get() = parseServicesField(servicesRaw)
}

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

    suspend fun getDoctorsBySpecialty(specialty: String): ApiResult<List<DoctorProfileRow>> {
        return safeApiCall {
            val result = supabaseClientProvider.client.from("doctor_profiles")
                .select {
                    filter { eq("specialty", specialty) }
                }
                .decodeList<DoctorProfileRow>()
            Log.d(TAG, "Fetched ${result.size} doctors for specialty=$specialty")
            result
        }
    }

    suspend fun updateAvailability(doctorId: String, isAvailable: Boolean): ApiResult<Unit> {
        return safeApiCall {
            supabaseClientProvider.client.from("doctor_profiles").update(
                buildJsonObject {
                    put("is_available", isAvailable)
                    put("updated_at", java.time.Instant.now().toString())
                },
            ) {
                filter { eq("doctor_id", doctorId) }
            }
            Log.d(TAG, "Updated availability for $doctorId to $isAvailable")
        }
    }

    companion object {
        private const val TAG = "DoctorProfileSvc"
    }
}

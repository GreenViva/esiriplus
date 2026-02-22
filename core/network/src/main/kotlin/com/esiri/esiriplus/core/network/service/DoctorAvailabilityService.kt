package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorAvailabilityService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {

    /**
     * Upsert the doctor's weekly availability schedule to the remote database.
     * Patients can then query this to see when the doctor is available.
     */
    suspend fun syncAvailability(
        doctorId: String,
        isAvailable: Boolean,
        scheduleJson: String,
    ) {
        try {
            supabaseClientProvider.client.from("doctor_availability").upsert(
                buildJsonObject {
                    put("availability_id", "${doctorId}_weekly")
                    put("doctor_id", doctorId)
                    put("is_available", isAvailable)
                    put("availability_schedule", kotlinx.serialization.json.Json.parseToJsonElement(scheduleJson))
                    put("updated_at", java.time.Instant.now().toString())
                },
            )
            Log.d(TAG, "Synced availability for doctor $doctorId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync availability for doctor $doctorId", e)
        }
    }

    /**
     * Fetch a doctor's availability schedule from the remote database.
     * Used by patients to see when a doctor is available for appointments.
     */
    suspend fun getAvailability(doctorId: String): JsonObject? {
        return try {
            val result = supabaseClientProvider.client.from("doctor_availability")
                .select {
                    filter { eq("doctor_id", doctorId) }
                }
                .decodeSingleOrNull<JsonObject>()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch availability for doctor $doctorId", e)
            null
        }
    }

    companion object {
        private const val TAG = "DoctorAvailabilityService"
    }
}

package com.esiri.esiriplus.core.network.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.repository.PatientSession
import com.esiri.esiriplus.core.domain.repository.PatientSessionRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Single source of truth for the patient's location. Pipeline:
 *
 *   FusedLocationProvider → Android Geocoder → resolve-patient-location edge fn
 *   → patient_sessions row (server) + Room mirror (client)
 *
 * Anything in the patient app that needs location (offer matching, pricing,
 * provider lookup) reads it off [PatientSessionRepository.getSession]; nothing
 * should ask the patient to type or pick. Run this once at permission grant
 * and again whenever the device's last-known location changes meaningfully.
 */
@Singleton
class LocationResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val edgeFunctionClient: EdgeFunctionClient,
    private val patientSessionRepository: PatientSessionRepository,
) {

    /**
     * Acquires GPS, reverse-geocodes, and persists the canonical hierarchy on
     * the active session (both server and Room). Caller must already hold
     * [android.Manifest.permission.ACCESS_COARSE_LOCATION] or fine. Returns
     * the resolved tuple on success, or a Result.Error if any step fails.
     */
    @SuppressLint("MissingPermission")
    suspend fun resolveAndPersist(): Result<ResolvedLocation> {
        val location = fetchCurrentLocation()
            ?: return Result.Error(IllegalStateException("No GPS fix"), "No GPS fix")

        val raw = reverseGeocode(location)
            ?: return Result.Error(IllegalStateException("Geocoder returned nothing"), "Geocoder failed")

        val session = patientSessionRepository.getSession().first()
            ?: return Result.Error(IllegalStateException("No active patient session"), "No session")

        return resolveAndSave(raw, session)
    }

    /** Re-resolve using already-known raw strings (e.g. from a cached Address). */
    suspend fun resolveAndPersist(raw: RawGeocodedLocation): Result<ResolvedLocation> {
        val session = patientSessionRepository.getSession().first()
            ?: return Result.Error(IllegalStateException("No active patient session"), "No session")
        return resolveAndSave(raw, session)
    }

    private suspend fun resolveAndSave(
        raw: RawGeocodedLocation,
        session: PatientSession,
    ): Result<ResolvedLocation> {
        val body = buildJsonObject {
            raw.region?.let   { put("region", it) }
            raw.district?.let { put("district", it) }
            raw.ward?.let     { put("ward", it) }
            raw.street?.let   { put("street", it) }
        }
        if (body.isEmpty()) {
            return Result.Error(IllegalArgumentException("No location data"), "Geocoder yielded nothing")
        }

        val response = edgeFunctionClient.invokeAndDecode<ResolverResponse>(
            functionName = "resolve-patient-location",
            body = body,
            patientAuth = true,
        )

        return when (response) {
            is ApiResult.Success -> {
                val resolved = ResolvedLocation(
                    region   = response.data.region,
                    district = response.data.district,
                    ward     = response.data.ward,
                    street   = response.data.street,
                )
                patientSessionRepository.updateLocation(
                    sessionId = session.sessionId,
                    region    = resolved.region,
                    district  = resolved.district,
                    ward      = resolved.ward,
                    street    = resolved.street,
                )
                Log.d(
                    TAG,
                    "Resolved raw=(${raw.region}/${raw.district}/${raw.ward}/${raw.street}) " +
                        "→ canonical=(${resolved.region}/${resolved.district}/${resolved.ward}/${resolved.street})",
                )
                Result.Success(resolved)
            }
            is ApiResult.Error -> {
                Log.w(TAG, "Resolver edge fn failed: ${response.message}")
                Result.Error(Exception(response.message), response.message)
            }
            is ApiResult.NetworkError -> {
                Log.w(TAG, "Resolver network error: ${response.message}")
                Result.Error(Exception(response.message), response.message)
            }
            is ApiResult.Unauthorized -> {
                Log.w(TAG, "Resolver unauthorized")
                Result.Error(Exception("Unauthorized"), "Unauthorized")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocation(): Location? =
        suspendCancellableCoroutine { cont: CancellableContinuation<Location?> ->
            try {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { _ -> if (cont.isActive) cont.resume(null) }
                    .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
            } catch (e: SecurityException) {
                Log.w(TAG, "Location permission missing", e)
                if (cont.isActive) cont.resume(null)
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(location: Location): RawGeocodedLocation? =
        withContext(Dispatchers.IO) {
            try {
                val addresses = Geocoder(context, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                val addr = addresses?.firstOrNull() ?: return@withContext null

                // Tanzania mapping convention used here (validated against Geocoder
                // output for Dar es Salaam):
                //   adminArea     → region   (e.g. "Dar es Salaam", "Arusha")
                //   subAdminArea  → district (e.g. "Kinondoni", "Ilala")
                //   locality      → ward     (e.g. "Sinza", "Kariakoo")
                //   subLocality   → street   (e.g. "Sinza B", "Mwananyamala A")
                // Each is nullable; the resolver edge fn matches whatever it can
                // and returns the canonical names back. Worst case some levels
                // come back null and offer matching falls back to broader scope.
                RawGeocodedLocation(
                    region   = addr.adminArea?.takeIf { it.isNotBlank() },
                    district = addr.subAdminArea?.takeIf { it.isNotBlank() },
                    ward     = addr.locality?.takeIf { it.isNotBlank() },
                    street   = addr.subLocality?.takeIf { it.isNotBlank() },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Reverse geocode failed", e)
                null
            }
        }

    data class RawGeocodedLocation(
        val region: String?,
        val district: String?,
        val ward: String?,
        val street: String?,
    )

    data class ResolvedLocation(
        val region: String?,
        val district: String?,
        val ward: String?,
        val street: String?,
    )

    @Serializable
    private data class ResolverResponse(
        @SerialName("region")   val region: String? = null,
        @SerialName("district") val district: String? = null,
        @SerialName("ward")     val ward: String? = null,
        @SerialName("street")   val street: String? = null,
    )

    companion object {
        private const val TAG = "LocationResolver"
    }
}

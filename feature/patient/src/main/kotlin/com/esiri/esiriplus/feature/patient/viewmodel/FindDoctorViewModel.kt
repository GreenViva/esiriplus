package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject

enum class AvailabilityFilter { ALL, ONLINE, OFFLINE }

data class FindDoctorUiState(
    val doctors: List<DoctorProfileEntity> = emptyList(),
    val filteredDoctors: List<DoctorProfileEntity> = emptyList(),
    val searchQuery: String = "",
    val availabilityFilter: AvailabilityFilter = AvailabilityFilter.ALL,
    val serviceCategory: String = "",
    val isLoading: Boolean = true,
    val slotCounts: Map<String, Int> = emptyMap(), // doctorId → used slots
)

// Maps service tier category codes to the Postgres service_type_enum values
// used in the doctor_profiles table (lowercase).
private val categoryToSpecialty = mapOf(
    "NURSE" to "nurse",
    "CLINICAL_OFFICER" to "clinical_officer",
    "PHARMACIST" to "pharmacist",
    "GP" to "gp",
    "SPECIALIST" to "specialist",
    "PSYCHOLOGIST" to "psychologist",
)

// Maps Postgres enum values to display names for the UI.
val specialtyDisplayNames = mapOf(
    "nurse" to "Nurse",
    "clinical_officer" to "Clinical Officer",
    "pharmacist" to "Pharmacist",
    "gp" to "General Practitioner",
    "specialist" to "Specialist",
    "psychologist" to "Psychologist",
)

@Serializable
private data class DoctorSlotsResponse(
    val slots: Map<String, SlotInfo> = emptyMap(),
)

@Serializable
private data class SlotInfo(
    val used: Int = 0,
    val available: Int = 10,
    val total: Int = 10,
)

@Serializable
private data class ListDoctorsResponse(
    val doctors: List<DoctorRow> = emptyList(),
)

@Serializable
private data class DoctorRow(
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("full_name") val fullName: String,
    val email: String = "",
    val phone: String = "",
    val specialty: String = "",
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
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@HiltViewModel
class FindDoctorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val doctorProfileDao: DoctorProfileDao,
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val serviceCategory: String = savedStateHandle["serviceCategory"] ?: ""
    private val doctorSpecialty: String = categoryToSpecialty[serviceCategory] ?: serviceCategory

    private val _uiState = MutableStateFlow(FindDoctorUiState(serviceCategory = serviceCategory))
    val uiState: StateFlow<FindDoctorUiState> = _uiState.asStateFlow()

    init {
        loadDoctors()
    }

    private fun loadDoctors() {
        // 1. Fetch from backend via edge function (safe for patient tokens) and cache to Room
        viewModelScope.launch {
            fetchDoctorsFromBackend()
        }

        // 2. Observe Room (reactive — updates when cache is written)
        viewModelScope.launch {
            doctorProfileDao.getBySpecialty(doctorSpecialty).collect { doctors ->
                _uiState.update {
                    it.copy(doctors = doctors, isLoading = false)
                }
                applyFilters()
                // Fetch slot counts once we have doctor IDs
                if (doctors.isNotEmpty()) {
                    fetchSlotCounts(doctors.map { it.doctorId })
                }
            }
        }
    }

    private suspend fun fetchSlotCounts(doctorIds: List<String>) {
        val body = buildJsonObject {
            putJsonArray("doctor_ids") {
                doctorIds.take(50).forEach { add(JsonPrimitive(it)) }
            }
        }
        when (val result = edgeFunctionClient.invoke("get-doctor-slots", body)) {
            is ApiResult.Success -> {
                try {
                    val response = json.decodeFromString<DoctorSlotsResponse>(result.data)
                    val counts = response.slots.mapValues { (_, slot) -> slot.used }
                    _uiState.update { it.copy(slotCounts = counts) }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse slot response", e)
                }
            }
            else -> Log.w(TAG, "Failed to fetch slot counts: $result")
        }
    }

    private suspend fun fetchDoctorsFromBackend() {
        val body = buildJsonObject { put("specialty", doctorSpecialty) }

        when (val result = edgeFunctionClient.invoke("list-doctors", body)) {
            is ApiResult.Success -> {
                try {
                    val response = json.decodeFromString<ListDoctorsResponse>(result.data)
                    if (response.doctors.isNotEmpty()) {
                        val entities = response.doctors.map { it.toEntity() }
                        doctorProfileDao.insertAll(entities)
                        Log.d(TAG, "Cached ${entities.size} doctors for $doctorSpecialty")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse list-doctors response", e)
                }
            }
            else -> Log.w(TAG, "Failed to fetch doctors from backend: $result")
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun updateAvailabilityFilter(filter: AvailabilityFilter) {
        _uiState.update { it.copy(availabilityFilter = filter) }
        applyFilters()
    }

    private fun applyFilters() {
        _uiState.update { state ->
            val query = state.searchQuery.lowercase().trim()
            val filtered = state.doctors.filter { doctor ->
                val matchesSearch = query.isEmpty() ||
                    doctor.fullName.lowercase().contains(query) ||
                    doctor.specialty.lowercase().contains(query) ||
                    doctor.bio.lowercase().contains(query)

                val matchesAvailability = when (state.availabilityFilter) {
                    AvailabilityFilter.ALL -> true
                    AvailabilityFilter.ONLINE -> doctor.isAvailable
                    AvailabilityFilter.OFFLINE -> !doctor.isAvailable
                }

                matchesSearch && matchesAvailability
            }
            state.copy(filteredDoctors = filtered)
        }
    }

    companion object {
        private const val TAG = "FindDoctorVM"
    }
}

private fun DoctorRow.toEntity(): DoctorProfileEntity {
    val now = System.currentTimeMillis()
    return DoctorProfileEntity(
        doctorId = doctorId,
        fullName = fullName,
        email = email,
        phone = phone,
        specialty = specialty,
        specialistField = specialistField,
        languages = languages,
        bio = bio,
        licenseNumber = licenseNumber,
        yearsExperience = yearsExperience,
        profilePhotoUrl = profilePhotoUrl,
        averageRating = averageRating,
        totalRatings = totalRatings,
        isVerified = isVerified,
        isAvailable = isAvailable,
        services = services,
        countryCode = countryCode,
        country = country,
        licenseDocumentUrl = licenseDocumentUrl,
        certificatesUrl = certificatesUrl,
        createdAt = parseTimestamp(createdAt) ?: now,
        updatedAt = parseTimestamp(updatedAt) ?: now,
    )
}

private fun parseTimestamp(value: String): Long? {
    return try {
        java.time.Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

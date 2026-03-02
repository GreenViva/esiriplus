package com.esiri.esiriplus.feature.admin.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.model.DoctorStatus
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.StringOrListSerializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

// ── Response DTOs ────────────────────────────────────────────────────────────

@Serializable
data class AdminDoctorRow(
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("full_name") val fullName: String,
    val email: String = "",
    val phone: String = "",
    val specialty: String = "",
    @SerialName("specialist_field") val specialistField: String? = null,
    @Serializable(with = StringOrListSerializer::class)
    val languages: List<String> = emptyList(),
    val bio: String = "",
    @SerialName("license_number") val licenseNumber: String = "",
    @SerialName("years_experience") val yearsExperience: Int = 0,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
    @SerialName("average_rating") val averageRating: Double = 0.0,
    @SerialName("total_ratings") val totalRatings: Int = 0,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_available") val isAvailable: Boolean = false,
    @Serializable(with = StringOrListSerializer::class)
    val services: List<String> = emptyList(),
    @SerialName("country_code") val countryCode: String = "+255",
    val country: String = "",
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("banned_at") val bannedAt: String? = null,
    @SerialName("ban_reason") val banReason: String? = null,
    @SerialName("suspended_until") val suspendedUntil: String? = null,
    @SerialName("suspension_reason") val suspensionReason: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("verification_status") val verificationStatus: String? = null,
    @SerialName("warning_message") val warningMessage: String? = null,
    @SerialName("warning_at") val warningAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
) {
    fun computeStatus(): DoctorStatus {
        val now = System.currentTimeMillis()
        return when {
            isBanned -> DoctorStatus.BANNED
            suspendedUntil != null && parseTimestamp(suspendedUntil) > now -> DoctorStatus.SUSPENDED
            !isVerified && !rejectionReason.isNullOrBlank() -> DoctorStatus.REJECTED
            !isVerified -> DoctorStatus.PENDING
            else -> DoctorStatus.ACTIVE
        }
    }
}

@Serializable
private data class ListAllDoctorsResponse(
    val doctors: List<AdminDoctorRow> = emptyList(),
    val stats: DoctorStats = DoctorStats(),
)

@Serializable
private data class ManageDoctorResponse(
    val success: Boolean = false,
    val action: String = "",
)

// ── UI State ─────────────────────────────────────────────────────────────────

data class AdminDoctorUiState(
    val doctors: List<AdminDoctorRow> = emptyList(),
    val filteredDoctors: List<AdminDoctorRow> = emptyList(),
    val stats: DoctorStats = DoctorStats(),
    val statusFilter: DoctorStatus? = null, // null = All
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val actionInProgress: Boolean = false,
    val actionResult: ActionResult? = null,
    val error: String? = null,
    val selectedDoctorId: String? = null,
)

data class ActionResult(
    val success: Boolean,
    val message: String,
)

@HiltViewModel
class AdminDoctorViewModel @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _uiState = MutableStateFlow(AdminDoctorUiState())
    val uiState: StateFlow<AdminDoctorUiState> = _uiState.asStateFlow()

    init {
        loadDoctors()
    }

    fun loadDoctors() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = edgeFunctionClient.invoke("list-all-doctors")) {
                is ApiResult.Success -> {
                    try {
                        val response = json.decodeFromString<ListAllDoctorsResponse>(result.data)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                doctors = response.doctors,
                                stats = response.stats,
                            )
                        }
                        applyFilters()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse doctors response", e)
                        _uiState.update {
                            it.copy(isLoading = false, error = "Failed to parse response")
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isLoading = false, error = "Network error: ${result.message}") }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(isLoading = false, error = "Unauthorized") }
                }
            }
        }
    }

    fun selectDoctor(doctorId: String) {
        _uiState.update { it.copy(selectedDoctorId = doctorId) }
    }

    fun getSelectedDoctor(): AdminDoctorRow? {
        val state = _uiState.value
        return state.doctors.find { it.doctorId == state.selectedDoctorId }
    }

    fun updateFilter(status: DoctorStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
        applyFilters()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun clearActionResult() {
        _uiState.update { it.copy(actionResult = null) }
    }

    private fun applyFilters() {
        _uiState.update { state ->
            val query = state.searchQuery.lowercase().trim()
            val filtered = state.doctors.filter { doctor ->
                val matchesSearch = query.isEmpty() ||
                    doctor.fullName.lowercase().contains(query) ||
                    doctor.specialty.lowercase().contains(query) ||
                    doctor.email.lowercase().contains(query)

                val matchesStatus = state.statusFilter == null ||
                    doctor.computeStatus() == state.statusFilter

                matchesSearch && matchesStatus
            }
            state.copy(filteredDoctors = filtered)
        }
    }

    // ── Admin actions ────────────────────────────────────────────────────────

    fun approveDoctor(doctorId: String) {
        executeAction(buildJsonObject {
            put("action", "approve")
            put("doctor_id", doctorId)
        }, "Doctor approved")
    }

    fun rejectDoctor(doctorId: String, reason: String) {
        executeAction(buildJsonObject {
            put("action", "reject")
            put("doctor_id", doctorId)
            put("reason", reason)
        }, "Doctor rejected")
    }

    fun banDoctor(doctorId: String, reason: String) {
        executeAction(buildJsonObject {
            put("action", "ban")
            put("doctor_id", doctorId)
            put("reason", reason)
        }, "Doctor banned")
    }

    fun suspendDoctor(doctorId: String, days: Int, reason: String) {
        executeAction(buildJsonObject {
            put("action", "suspend")
            put("doctor_id", doctorId)
            put("days", days)
            put("reason", reason)
        }, "Doctor suspended for $days days")
    }

    fun warnDoctor(doctorId: String, message: String) {
        executeAction(buildJsonObject {
            put("action", "warn")
            put("doctor_id", doctorId)
            put("message", message)
        }, "Warning sent to doctor")
    }

    fun unsuspendDoctor(doctorId: String) {
        executeAction(buildJsonObject {
            put("action", "unsuspend")
            put("doctor_id", doctorId)
        }, "Doctor unsuspended")
    }

    fun unbanDoctor(doctorId: String) {
        executeAction(buildJsonObject {
            put("action", "unban")
            put("doctor_id", doctorId)
        }, "Doctor unbanned")
    }

    private fun executeAction(body: kotlinx.serialization.json.JsonObject, successMessage: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, actionResult = null) }
            when (val result = edgeFunctionClient.invoke("manage-doctor", body)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            actionResult = ActionResult(true, successMessage),
                        )
                    }
                    loadDoctors() // refresh list
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            actionResult = ActionResult(false, result.message),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            actionResult = ActionResult(false, "Network error: ${result.message}"),
                        )
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(
                            actionInProgress = false,
                            actionResult = ActionResult(false, "Unauthorized"),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AdminDoctorVM"
    }
}

private fun parseTimestamp(value: String): Long {
    return try {
        java.time.Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        0L
    }
}

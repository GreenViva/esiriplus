package com.esiri.esiriplus.feature.admin.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

// -- Response DTOs ---------------------------------------------------------------

@Serializable
data class AuditLogsResponse(
    val logs: List<AuditLogEntry> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
)

@Serializable
data class AuditLogEntry(
    val id: String,
    @SerialName("admin_id") val adminId: String,
    @SerialName("admin_email") val adminEmail: String = "",
    val action: String,
    @SerialName("target_type") val targetType: String? = null,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("target_name") val targetName: String? = null,
    val details: JsonObject? = null,
    @SerialName("created_at") val createdAt: String,
)

// -- Action filter categories (matching web panel) --------------------------------

enum class ActionCategory(val label: String) {
    ALL("All Actions"),
    APPROVE("Approve / Verify"),
    REJECT("Reject"),
    SUSPEND("Suspend"),
    BAN("Ban"),
    WARN("Warn"),
    CREATE("Create / Assign"),
    FLAG("Flag"),
    CONSULTATION("Consultation"),
    PAYMENT("Payment"),
    RATING("Rating"),
    REGISTRATION("Registration"),
    OTHER("Other"),
}

// -- UI State --------------------------------------------------------------------

data class AuditLogUiState(
    val logs: List<AuditLogEntry> = emptyList(),
    val filteredLogs: List<AuditLogEntry> = emptyList(),
    val totalCount: Int = 0,
    val searchQuery: String = "",
    val actionFilter: ActionCategory = ActionCategory.ALL,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadLogs()
    }

    fun loadLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val body = buildJsonObject {
                put("limit", 200)
                put("offset", 0)
            }

            when (val result = edgeFunctionClient.invoke("get-audit-logs", body)) {
                is ApiResult.Success -> {
                    try {
                        val response = json.decodeFromString<AuditLogsResponse>(result.data)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                logs = response.logs,
                                totalCount = response.totalCount,
                            )
                        }
                        applyFilters()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse audit logs response", e)
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

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            applyFilters()
        }
    }

    fun updateActionFilter(category: ActionCategory) {
        _uiState.update { it.copy(actionFilter = category) }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        var list = state.logs

        // Search filter (client-side, matching web panel logic)
        val query = state.searchQuery.trim().lowercase()
        if (query.isNotEmpty()) {
            list = list.filter { log ->
                log.adminEmail.lowercase().contains(query) ||
                    log.action.lowercase().contains(query) ||
                    formatActionDescription(log).lowercase().contains(query) ||
                    (log.targetType ?: "").lowercase().contains(query)
            }
        }

        // Action category filter
        if (state.actionFilter != ActionCategory.ALL) {
            list = list.filter { log ->
                getActionCategory(log.action) == state.actionFilter
            }
        }

        _uiState.update { it.copy(filteredLogs = list) }
    }

    companion object {
        private const val TAG = "AuditLogVM"
        private const val SEARCH_DEBOUNCE_MS = 400L

        fun getActionCategory(action: String): ActionCategory {
            return when {
                action.contains("unsuspend") || action == "unban_doctor" -> ActionCategory.APPROVE
                action.contains("approve") || action == "doctor_verified" -> ActionCategory.APPROVE
                action.contains("reject") || action == "doctor_rejected" -> ActionCategory.REJECT
                action.contains("suspend") || action == "doctor_deactivated" -> ActionCategory.SUSPEND
                action.contains("ban") -> ActionCategory.BAN
                action.contains("warn") -> ActionCategory.WARN
                action.contains("create") || action.contains("setup") || action == "role_assigned" -> ActionCategory.CREATE
                action.contains("flag") -> ActionCategory.FLAG
                action.contains("consultation") -> ActionCategory.CONSULTATION
                action.contains("payment") -> ActionCategory.PAYMENT
                action.contains("rating") -> ActionCategory.RATING
                action.contains("register") || action == "doctor_registered" || action == "patient_session_created" -> ActionCategory.REGISTRATION
                else -> ActionCategory.OTHER
            }
        }

        fun formatActionDescription(log: AuditLogEntry): String {
            val d = log.details
            val targetName = log.targetName
            val name = targetName
                ?: d?.get("full_name")?.jsonPrimitive?.content
                ?: log.targetId?.take(8)
                ?: "unknown"

            return when (log.action) {
                // Admin/HR manual actions
                "approve_doctor" -> "Approved doctor: $name"
                "reject_doctor" -> {
                    val reason = d?.get("reason")?.jsonPrimitive?.content
                    "Rejected doctor: $name${if (reason != null) " — $reason" else ""}"
                }
                "suspend_doctor" -> "Suspended doctor: $name"
                "ban_doctor" -> "Banned doctor: $name"
                "warn_doctor" -> {
                    val message = d?.get("message")?.jsonPrimitive?.content
                    "Warned doctor: $name${if (message != null) " — \"$message\"" else ""}"
                }
                "suspend_user" -> "Suspended user ${log.targetId?.take(8) ?: "unknown"}"
                "unsuspend_user" -> "Unsuspended user ${log.targetId?.take(8) ?: "unknown"}"
                "create_portal_user", "portal_user_created" -> {
                    val role = d?.get("role")?.jsonPrimitive?.content ?: "user"
                    val email = d?.get("email")?.jsonPrimitive?.content ?: "unknown"
                    "Created $role account: $email"
                }
                "initial_admin_setup" -> {
                    val email = d?.get("email")?.jsonPrimitive?.content ?: ""
                    "Initial admin setup: $email"
                }
                "flag_rating" -> "Flagged rating ${log.targetId?.take(8) ?: ""}"
                "unflag_rating" -> "Unflagged rating ${log.targetId?.take(8) ?: ""}"
                "unsuspend_doctor" -> "Unsuspended doctor: $name"
                "unban_doctor" -> "Unbanned doctor: $name"
                "deauthorize_device" -> "Deauthorized device for doctor $name"
                "delete_user_role" -> {
                    val role = d?.get("role")?.jsonPrimitive?.content ?: "unknown"
                    "Removed $role role from user ${log.targetId?.take(8) ?: "unknown"}"
                }
                // Auto-triggered events
                "doctor_registered" -> {
                    val fullName = d?.get("full_name")?.jsonPrimitive?.content ?: name
                    val specialty = d?.get("specialty")?.jsonPrimitive?.content ?: ""
                    "New doctor registered: $fullName${if (specialty.isNotEmpty()) " ($specialty)" else ""}"
                }
                "doctor_verified" -> "Doctor verified: ${d?.get("full_name")?.jsonPrimitive?.content ?: name}"
                "doctor_activated" -> "Doctor activated: ${d?.get("full_name")?.jsonPrimitive?.content ?: name}"
                "doctor_deactivated" -> "Doctor deactivated: ${d?.get("full_name")?.jsonPrimitive?.content ?: name}"
                "doctor_rejected" -> {
                    val reason = d?.get("reason")?.jsonPrimitive?.content
                    "Doctor rejected: ${d?.get("full_name")?.jsonPrimitive?.content ?: name}${if (reason != null) " — $reason" else ""}"
                }
                "consultation_created" -> {
                    val svcType = d?.get("service_type")?.jsonPrimitive?.content ?: "general"
                    "New consultation created ($svcType)"
                }
                "consultation_active", "consultation_in_progress" -> {
                    val status = d?.get("new_status")?.jsonPrimitive?.content ?: "active"
                    "Consultation started ($status)"
                }
                "consultation_completed" -> "Consultation completed"
                "consultation_cancelled" -> "Consultation cancelled"
                "consultation_pending" -> "Consultation pending"
                "payment_created" -> {
                    val amount = d?.get("amount")?.jsonPrimitive?.content ?: "0"
                    val currency = d?.get("currency")?.jsonPrimitive?.content ?: "TZS"
                    "Payment of $amount $currency created"
                }
                "payment_completed" -> {
                    val amount = d?.get("amount")?.jsonPrimitive?.content ?: "0"
                    val currency = d?.get("currency")?.jsonPrimitive?.content ?: "TZS"
                    "Payment of $amount $currency completed"
                }
                "rating_submitted" -> {
                    val rating = d?.get("rating")?.jsonPrimitive?.content ?: "?"
                    val hasComment = d?.get("has_comment")?.jsonPrimitive?.content == "true"
                    "Patient rated doctor ${rating}\u2605${if (hasComment) " with comment" else ""}"
                }
                "patient_session_created" -> {
                    val region = d?.get("region")?.jsonPrimitive?.content
                    "New patient session${if (region != null) " from $region" else ""}"
                }
                "role_assigned" -> "Role \"${d?.get("role")?.jsonPrimitive?.content ?: "unknown"}\" assigned to user"
                "role_revoked" -> "Role \"${d?.get("role")?.jsonPrimitive?.content ?: "unknown"}\" revoked from user"
                else -> log.action.replace("_", " ")
            }
        }

        fun getTypeBadgeLabel(action: String): String {
            return when {
                action.contains("unsuspend") || action == "unban_doctor" -> "restore"
                action.contains("unflag") -> "unflag"
                action.contains("approve") || action == "doctor_verified" -> "approve"
                action.contains("reject") || action == "doctor_rejected" -> "reject"
                action == "doctor_deactivated" -> "deactivate"
                action == "doctor_activated" -> "activate"
                action.contains("suspend") -> "suspend"
                action.contains("ban") -> "ban"
                action.contains("warn") -> "warn"
                action.contains("create") || action.contains("setup") || action == "role_assigned" -> "create"
                action.contains("flag") -> "flag"
                action.contains("revoke") || action == "role_revoked" -> "revoke"
                action.contains("deauthorize") -> "deauthorize"
                action == "doctor_registered" || action == "patient_session_created" -> "registration"
                action.contains("consultation") -> "consultation"
                action.contains("payment") -> "payment"
                action.contains("rating") -> "rating"
                else -> action.replace("_", " ")
            }
        }
    }
}

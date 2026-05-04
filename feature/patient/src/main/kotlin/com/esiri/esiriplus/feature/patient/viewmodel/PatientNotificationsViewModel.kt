package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.NotificationDao
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.NotificationSyncService
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class PatientNotificationsUiState(
    val notifications: List<NotificationEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val unreadCount: Int = 0,
)

/**
 * Patient-side notification feed. Mirrors DoctorNotificationsViewModel but
 * keys on the JWT's session_id (patients don't have user accounts). The
 * notifications row's user_id column stores the session_id for patient
 * pushes — see send-push-notification's targetUserId logic.
 */
@HiltViewModel
class PatientNotificationsViewModel @Inject constructor(
    private val notificationDao: NotificationDao,
    private val notificationSyncService: NotificationSyncService,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val tokenManager: TokenManager,
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientNotificationsUiState())
    val uiState: StateFlow<PatientNotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            val sessionId = sessionIdFromToken() ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            launch {
                notificationDao.getForUser(sessionId).collect { rows ->
                    _uiState.update {
                        it.copy(
                            notifications = rows,
                            isLoading = false,
                            unreadCount = rows.count { n -> n.readAt == null },
                        )
                    }
                }
            }

            fetchFromSupabase(sessionId)
        }
    }

    private suspend fun fetchFromSupabase(sessionId: String) {
        try {
            val access = tokenManager.getAccessTokenSync()
            val refresh = tokenManager.getRefreshTokenSync()
            if (access != null && refresh != null) {
                supabaseClientProvider.importAuthToken(access, refresh)
            }

            when (val result = notificationSyncService.fetchRecentNotifications(sessionId)) {
                is ApiResult.Success -> {
                    val entities = result.data.map { row ->
                        NotificationEntity(
                            notificationId = row.notificationId,
                            userId = row.userId,
                            title = row.title,
                            body = row.body,
                            type = row.type,
                            data = row.data,
                            readAt = row.readAt?.let(::parseInstant),
                            createdAt = parseInstant(row.createdAt) ?: System.currentTimeMillis(),
                        )
                    }
                    if (entities.isNotEmpty()) {
                        notificationDao.insertAll(entities)
                    }
                }
                else -> Log.w(TAG, "Notification sync failed: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification sync error", e)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val sessionId = sessionIdFromToken() ?: return@launch
                fetchFromSupabase(sessionId)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationDao.markAsRead(notificationId, System.currentTimeMillis())
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val sessionId = sessionIdFromToken() ?: return@launch
            notificationDao.markAllAsRead(sessionId, System.currentTimeMillis())
        }
    }

    /**
     * Local + remote delete. Removes from Room immediately for snappy UX
     * and fires the delete-notification edge function so the row is gone
     * server-side too — otherwise pull-to-refresh would resurrect it.
     */
    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            val existing = notificationDao.getById(notificationId)
            if (existing != null) notificationDao.delete(existing)
            try {
                val body = buildJsonObject { put("notification_id", notificationId) }
                edgeFunctionClient.invoke("delete-notification", body)
            } catch (e: Exception) {
                Log.w(TAG, "Remote delete failed (local already removed)", e)
            }
        }
    }

    private fun sessionIdFromToken(): String? {
        val token = tokenManager.getAccessTokenSync() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING),
            )
            JSONObject(payload).optString("session_id", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode session_id from JWT", e)
            null
        }
    }

    private fun parseInstant(iso: String): Long? = try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) { null }

    companion object {
        private const val TAG = "PatientNotifVM"
    }
}

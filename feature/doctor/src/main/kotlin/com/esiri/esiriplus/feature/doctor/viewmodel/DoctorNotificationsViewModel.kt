package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.NotificationDao
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.NotificationSyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val notifications: List<NotificationEntity> = emptyList(),
    val isLoading: Boolean = true,
    val unreadCount: Int = 0,
)

@HiltViewModel
class DoctorNotificationsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationDao: NotificationDao,
    private val notificationSyncService: NotificationSyncService,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first() ?: return@launch
            val userId = session.user.id

            // Observe local Room data reactively
            launch {
                notificationDao.getForUser(userId).collect { notifications ->
                    _uiState.update {
                        it.copy(
                            notifications = notifications,
                            isLoading = false,
                            unreadCount = notifications.count { n -> n.readAt == null },
                        )
                    }
                }
            }

            // Fetch from Supabase and cache locally
            fetchFromSupabase(userId, session.accessToken, session.refreshToken)
        }
    }

    private suspend fun fetchFromSupabase(userId: String, accessToken: String, refreshToken: String) {
        try {
            val freshAccess = tokenManager.getAccessTokenSync() ?: accessToken
            val freshRefresh = tokenManager.getRefreshTokenSync() ?: refreshToken
            supabaseClientProvider.importAuthToken(freshAccess, freshRefresh)

            when (val result = notificationSyncService.fetchRecentNotifications(userId)) {
                is ApiResult.Success -> {
                    val entities = result.data.map { row ->
                        NotificationEntity(
                            notificationId = row.notificationId,
                            userId = row.userId,
                            title = row.title,
                            body = row.body,
                            type = row.type,
                            data = row.data ?: "{}",
                            readAt = row.readAt?.let { parseInstant(it) },
                            createdAt = parseInstant(row.createdAt) ?: System.currentTimeMillis(),
                        )
                    }
                    if (entities.isNotEmpty()) {
                        notificationDao.insertAll(entities)
                        Log.d(TAG, "Cached ${entities.size} notifications from Supabase")
                    }
                }
                else -> Log.e(TAG, "Failed to fetch notifications: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch notifications from Supabase", e)
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationDao.markAsRead(notificationId, System.currentTimeMillis())
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first() ?: return@launch
            notificationDao.markAllAsRead(session.user.id, System.currentTimeMillis())
        }
    }

    private fun parseInstant(isoString: String): Long? {
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "DoctorNotifVM"
    }
}

package com.esiri.esiriplus.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.model.Notification
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

data class NotificationUiState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val syncError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    /** Unread badge count — emits reactively from Room via Flow. */
    val unreadCount: StateFlow<Int> = authRepository.currentSession
        .flatMapLatest { session ->
            val userId = session?.user?.id ?: return@flatMapLatest flowOf(0)
            notificationRepository.getUnreadCount(userId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    init {
        loadNotifications()
        syncOnStartup()
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            authRepository.currentSession.flatMapLatest { session ->
                val userId = session?.user?.id ?: return@flatMapLatest flowOf(emptyList())
                notificationRepository.getNotificationsForUser(userId)
            }.collect { notifications ->
                _uiState.value = _uiState.value.copy(
                    notifications = notifications,
                    isLoading = false,
                )
            }
        }
    }

    /** Sync unread notifications from Supabase on app open. */
    private fun syncOnStartup() {
        viewModelScope.launch {
            val session = authRepository.currentSession
                .map { it }
                .stateIn(viewModelScope)
                .value ?: return@launch
            val userId = session.user.id

            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                notificationRepository.syncFromRemote(userId)
                _uiState.value = _uiState.value.copy(syncError = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync notifications", e)
                _uiState.value = _uiState.value.copy(syncError = true)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val session = authRepository.currentSession
                .map { it }
                .stateIn(viewModelScope)
                .value ?: return@launch
            notificationRepository.markAllAsRead(session.user.id)
        }
    }

    fun refresh() {
        syncOnStartup()
    }

    companion object {
        private const val TAG = "NotificationVM"
    }
}

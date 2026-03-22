package com.esiri.esiriplus.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.init.DatabaseInitState
import com.esiri.esiriplus.core.database.init.DatabaseInitializer
import com.esiri.esiriplus.core.domain.model.AuthState
import com.esiri.esiriplus.core.domain.usecase.LogoutUseCase
import com.esiri.esiriplus.core.domain.usecase.ObserveAuthStateUseCase
import com.esiri.esiriplus.core.domain.usecase.RefreshSessionUseCase
import com.esiri.esiriplus.core.common.network.NetworkMonitor
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.lifecycle.BiometricLockStateHolder
import com.esiri.esiriplus.worker.SyncScheduler
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeAuthState: ObserveAuthStateUseCase,
    private val refreshSession: RefreshSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val databaseInitializer: DatabaseInitializer,
    private val biometricLockStateHolder: BiometricLockStateHolder,
    private val edgeFunctionClient: EdgeFunctionClient,
    networkMonitor: NetworkMonitor,
    syncScheduler: SyncScheduler,
) : ViewModel() {

    private var fcmTokenSynced = false

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _appInitState = MutableStateFlow<AppInitState>(AppInitState.Loading)
    val appInitState: StateFlow<AppInitState> = _appInitState.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        syncScheduler.start(viewModelScope)
        viewModelScope.launch {
            val result = databaseInitializer.initialize()
            when (result) {
                is DatabaseInitState.Ready -> {
                    _appInitState.value = AppInitState.Ready
                    observeAuth()
                }
                is DatabaseInitState.Failed -> {
                    _appInitState.value = AppInitState.DatabaseError(result.error)
                }
                else -> {}
            }
        }
    }

    fun onLogout() {
        Log.w("MainViewModel", "onLogout() called!", Exception("Logout stack trace"))
        viewModelScope.launch {
            biometricLockStateHolder.setLocked(true)
            logoutUseCase()
        }
    }

    private suspend fun observeAuth() {
        observeAuthState().collect { state ->
            when (state) {
                is AuthState.SessionExpired -> handleSessionExpired()
                is AuthState.Authenticated -> {
                    _authState.value = state
                    syncFcmTokenIfNeeded()
                }
                else -> _authState.value = state
            }
        }
    }

    private fun syncFcmTokenIfNeeded() {
        if (fcmTokenSynced) return
        fcmTokenSynced = true
        viewModelScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                val body = buildJsonObject { put("fcm_token", JsonPrimitive(token)) }
                edgeFunctionClient.invoke("update-fcm-token", body)
                Log.d("MainViewModel", "FCM token synced on app launch")
            } catch (e: Exception) {
                Log.w("MainViewModel", "FCM token sync failed", e)
                fcmTokenSynced = false // retry next time
            }
        }
    }

    private suspend fun handleSessionExpired() {
        val result = refreshSession()
        if (result is Result.Error) {
            // Emit SessionExpired so NavHost can decide what to do.
            // Do NOT call logoutUseCase() here — that clears the session and
            // emits Unauthenticated, which would yank the user off protected
            // screens (chat, payment). The NavHost will only navigate away
            // if the user is NOT on a protected screen.
            _authState.value = AuthState.SessionExpired
        }
    }
}

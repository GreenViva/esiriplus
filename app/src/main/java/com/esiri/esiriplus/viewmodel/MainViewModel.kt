package com.esiri.esiriplus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.init.DatabaseInitState
import com.esiri.esiriplus.core.database.init.DatabaseInitializer
import com.esiri.esiriplus.core.domain.model.AuthState
import com.esiri.esiriplus.core.domain.usecase.LogoutUseCase
import com.esiri.esiriplus.core.domain.usecase.ObserveAuthStateUseCase
import com.esiri.esiriplus.core.domain.usecase.RefreshSessionUseCase
import com.esiri.esiriplus.lifecycle.BiometricLockStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeAuthState: ObserveAuthStateUseCase,
    private val refreshSession: RefreshSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val databaseInitializer: DatabaseInitializer,
    private val biometricLockStateHolder: BiometricLockStateHolder,
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _appInitState = MutableStateFlow<AppInitState>(AppInitState.Loading)
    val appInitState: StateFlow<AppInitState> = _appInitState.asStateFlow()

    init {
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
        viewModelScope.launch {
            biometricLockStateHolder.setLocked(true)
            logoutUseCase()
        }
    }

    private suspend fun observeAuth() {
        observeAuthState().collect { state ->
            when (state) {
                is AuthState.SessionExpired -> handleSessionExpired()
                else -> _authState.value = state
            }
        }
    }

    private suspend fun handleSessionExpired() {
        val result = refreshSession()
        if (result is Result.Error) {
            logoutUseCase()
        }
    }
}

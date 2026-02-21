package com.esiri.esiriplus.core.domain.model

sealed interface AuthState {
    data object Loading : AuthState
    data class Authenticated(val session: Session) : AuthState
    data object Unauthenticated : AuthState
    data object SessionExpired : AuthState
}

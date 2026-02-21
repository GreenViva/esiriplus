package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.domain.model.AuthState
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveAuthStateUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<AuthState> = authRepository.currentSession.map { session ->
        when {
            session == null -> AuthState.Unauthenticated
            session.isRefreshWindowExpired -> AuthState.SessionExpired
            session.isExpired -> AuthState.SessionExpired
            else -> AuthState.Authenticated(session)
        }
    }
}

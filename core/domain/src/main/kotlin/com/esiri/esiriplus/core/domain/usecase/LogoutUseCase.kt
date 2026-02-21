package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.logout()
}

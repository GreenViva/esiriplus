package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import javax.inject.Inject

class LoginDoctorUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): Result<Session> =
        authRepository.loginDoctor(email, password)
}

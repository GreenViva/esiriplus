package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.DoctorRegistration
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterDoctorUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(registration: DoctorRegistration): Result<Session> =
        authRepository.registerDoctor(registration)
}

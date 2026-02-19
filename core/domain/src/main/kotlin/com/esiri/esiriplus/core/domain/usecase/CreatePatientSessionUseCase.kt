package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import javax.inject.Inject

class CreatePatientSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(phone: String, fullName: String): Result<Session> =
        authRepository.createPatientSession(phone, fullName)
}

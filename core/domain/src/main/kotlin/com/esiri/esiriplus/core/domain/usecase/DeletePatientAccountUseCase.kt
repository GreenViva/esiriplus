package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.domain.repository.AuthRepository
import javax.inject.Inject

class DeletePatientAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Boolean = authRepository.deletePatientAccount()
}

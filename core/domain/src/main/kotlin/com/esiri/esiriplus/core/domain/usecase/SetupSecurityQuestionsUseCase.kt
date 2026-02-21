package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import javax.inject.Inject

class SetupSecurityQuestionsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(answers: Map<String, String>): Result<Unit> =
        authRepository.setupSecurityQuestions(answers)
}

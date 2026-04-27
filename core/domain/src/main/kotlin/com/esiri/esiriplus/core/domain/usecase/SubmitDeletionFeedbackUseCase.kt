package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.domain.repository.AuthRepository
import javax.inject.Inject

class SubmitDeletionFeedbackUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(reasonCodes: List<String>, comment: String?): Boolean =
        authRepository.submitDeletionFeedback(reasonCodes, comment)
}

package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.SecurityQuestion
import com.esiri.esiriplus.core.domain.repository.SecurityQuestionRepository
import javax.inject.Inject

class GetSecurityQuestionsUseCase @Inject constructor(
    private val securityQuestionRepository: SecurityQuestionRepository,
) {
    suspend operator fun invoke(): Result<List<SecurityQuestion>> =
        securityQuestionRepository.getSecurityQuestions()
}

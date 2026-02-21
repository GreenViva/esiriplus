package com.esiri.esiriplus.feature.auth.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.SecurityQuestion
import com.esiri.esiriplus.core.domain.repository.SecurityQuestionRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.dto.SecurityQuestionDto
import com.esiri.esiriplus.core.network.dto.toDomain
import com.esiri.esiriplus.core.network.model.toDomainResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityQuestionRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : SecurityQuestionRepository {

    override suspend fun getSecurityQuestions(): Result<List<SecurityQuestion>> =
        edgeFunctionClient.invokeAndDecode<List<SecurityQuestionDto>>(
            functionName = "get-security-questions",
        ).map { dtos -> dtos.map { it.toDomain() } }.toDomainResult()
}

package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.SecurityQuestion

interface SecurityQuestionRepository {
    suspend fun getSecurityQuestions(): Result<List<SecurityQuestion>>
}

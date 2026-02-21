package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentSession: Flow<Session?>
    suspend fun createPatientSession(): Result<Session>
    suspend fun loginDoctor(email: String, password: String): Result<Session>
    suspend fun refreshSession(): Result<Session>
    suspend fun logout()
    suspend fun recoverPatientSession(answers: Map<String, String>): Result<Session>
    suspend fun setupSecurityQuestions(answers: Map<String, String>): Result<Unit>
}

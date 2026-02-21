package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.domain.repository.AuthRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LogoutUseCaseTest {

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val useCase = LogoutUseCase(authRepository)

    @Test
    fun `delegates to auth repository logout`() = runTest {
        useCase()
        coVerify { authRepository.logout() }
    }
}

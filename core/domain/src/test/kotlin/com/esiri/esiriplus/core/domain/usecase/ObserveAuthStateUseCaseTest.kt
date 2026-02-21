package com.esiri.esiriplus.core.domain.usecase

import app.cash.turbine.test
import com.esiri.esiriplus.core.domain.model.AuthState
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ObserveAuthStateUseCaseTest {

    private val authRepository = mockk<AuthRepository>()
    private val useCase = ObserveAuthStateUseCase(authRepository)

    private val testUser = User(
        id = "user-1",
        fullName = "Test User",
        phone = "+255700000000",
        role = UserRole.PATIENT,
    )

    @Test
    fun `emits Unauthenticated when session is null`() = runTest {
        every { authRepository.currentSession } returns flowOf(null)

        useCase().test {
            assertEquals(AuthState.Unauthenticated, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `emits Authenticated when session is valid`() = runTest {
        val session = Session(
            accessToken = "token",
            refreshToken = "refresh",
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS),
            user = testUser,
            createdAt = Instant.now(),
        )
        every { authRepository.currentSession } returns flowOf(session)

        useCase().test {
            val state = awaitItem()
            assertTrue(state is AuthState.Authenticated)
            assertEquals(session, (state as AuthState.Authenticated).session)
            awaitComplete()
        }
    }

    @Test
    fun `emits SessionExpired when access token is expired`() = runTest {
        val session = Session(
            accessToken = "token",
            refreshToken = "refresh",
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
            user = testUser,
            createdAt = Instant.now(),
        )
        every { authRepository.currentSession } returns flowOf(session)

        useCase().test {
            assertEquals(AuthState.SessionExpired, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `emits SessionExpired when refresh window is expired`() = runTest {
        val session = Session(
            accessToken = "token",
            refreshToken = "refresh",
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS),
            user = testUser,
            createdAt = Instant.now().minus(8, ChronoUnit.DAYS),
        )
        every { authRepository.currentSession } returns flowOf(session)

        useCase().test {
            assertEquals(AuthState.SessionExpired, awaitItem())
            awaitComplete()
        }
    }
}

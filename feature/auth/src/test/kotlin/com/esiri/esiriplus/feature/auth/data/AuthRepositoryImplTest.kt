package com.esiri.esiriplus.feature.auth.data

import app.cash.turbine.test
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.dao.SessionDao
import com.esiri.esiriplus.core.database.dao.UserDao
import com.esiri.esiriplus.core.database.entity.SessionEntity
import com.esiri.esiriplus.core.database.entity.UserEntity
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.model.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AuthRepositoryImplTest {

    private lateinit var edgeFunctionClient: EdgeFunctionClient
    private lateinit var tokenManager: TokenManager
    private lateinit var sessionDao: SessionDao
    private lateinit var userDao: UserDao
    private lateinit var database: EsiriplusDatabase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        edgeFunctionClient = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
        sessionDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        database = mockk(relaxed = true)
    }

    @Test
    fun `currentSession emits null when no session in DB`() = runTest(testDispatcher) {
        every { sessionDao.getCurrentSession() } returns flowOf(null)
        val repository = createRepository()

        repository.currentSession.test {
            assertNull(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `currentSession emits session from Room when data exists`() = runTest(testDispatcher) {
        val sessionEntity = SessionEntity(
            accessToken = "token",
            refreshToken = "refresh",
            expiresAt = Instant.now().plusSeconds(3600),
            userId = "user-1",
            createdAt = Instant.now(),
        )
        val userEntity = UserEntity(
            id = "user-1",
            fullName = "Test User",
            phone = "+255700000000",
            email = null,
            role = "PATIENT",
            isVerified = false,
        )
        every { sessionDao.getCurrentSession() } returns flowOf(sessionEntity)
        every { userDao.getUserById("user-1") } returns flowOf(userEntity)
        val repository = createRepository()

        repository.currentSession.test {
            val session = awaitItem()
            assertNotNull(session)
            assertEquals("user-1", session?.user?.id)
            awaitComplete()
        }
    }

    @Test
    fun `logout clears tokens and database`() = runTest(testDispatcher) {
        coEvery { edgeFunctionClient.invoke("logout", any()) } returns ApiResult.Success("ok")
        val repository = createRepository()

        repository.logout()

        verify { tokenManager.clearTokens() }
        coVerify { database.clearAllTables() }
    }

    @Test
    fun `logout clears local state even when server call fails`() = runTest(testDispatcher) {
        coEvery {
            edgeFunctionClient.invoke("logout", any())
        } throws RuntimeException("Network error")
        val repository = createRepository()

        repository.logout()

        verify { tokenManager.clearTokens() }
        coVerify { database.clearAllTables() }
    }

    @Test
    fun `refreshSession returns error when no refresh token`() = runTest(testDispatcher) {
        every { tokenManager.getRefreshTokenSync() } returns null
        val repository = createRepository()

        val result = repository.refreshSession()

        assertTrue(result is Result.Error)
    }

    @Test
    fun `setupSecurityQuestions calls edge function`() = runTest(testDispatcher) {
        coEvery {
            edgeFunctionClient.invoke(
                functionName = "setup-recovery",
                body = any(),
            )
        } returns ApiResult.Success("ok")
        val repository = createRepository()

        val result = repository.setupSecurityQuestions(
            mapOf("first_pet" to "Simba"),
        )

        assertTrue(result is Result.Success)
    }

    private fun createRepository() = AuthRepositoryImpl(
        edgeFunctionClient = edgeFunctionClient,
        tokenManager = tokenManager,
        sessionDao = sessionDao,
        userDao = userDao,
        database = database,
        ioDispatcher = testDispatcher,
    )
}

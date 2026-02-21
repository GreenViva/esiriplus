package com.esiri.esiriplus.feature.auth.viewmodel

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.domain.usecase.RecoverPatientSessionUseCase
import com.esiri.esiriplus.feature.auth.recovery.RecoveryRateLimiter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PatientRecoveryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var recoverPatientSession: RecoverPatientSessionUseCase
    private lateinit var rateLimiter: RecoveryRateLimiter

    private val testUser = User(
        id = "patient-123",
        fullName = "Test Patient",
        phone = "+255700000000",
        role = UserRole.PATIENT,
    )

    private val testSession = Session(
        accessToken = "token",
        refreshToken = "refresh",
        expiresAt = Instant.now().plusSeconds(3600),
        user = testUser,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        recoverPatientSession = mockk()
        rateLimiter = mockk(relaxed = true)
        every { rateLimiter.canAttempt() } returns true
        every { rateLimiter.remainingAttempts() } returns 5
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct defaults`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(0, state.currentQuestionIndex)
        assertTrue(state.answers.isEmpty())
        assertEquals("", state.currentAnswer)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.isRateLimited)
    }

    @Test
    fun `onNext advances to next question`() {
        val viewModel = createViewModel()

        viewModel.onAnswerChanged("Dar es Salaam")
        viewModel.onNext()

        val state = viewModel.uiState.value
        assertEquals(1, state.currentQuestionIndex)
        assertEquals("", state.currentAnswer)
        assertEquals(1, state.answers.size)
    }

    @Test
    fun `onNext does nothing when answer is blank`() {
        val viewModel = createViewModel()

        viewModel.onNext()

        assertEquals(0, viewModel.uiState.value.currentQuestionIndex)
    }

    @Test
    fun `submits on last question and sets recoveredPatientId on success`() = runTest {
        coEvery { recoverPatientSession(any()) } returns Result.Success(testSession)

        val viewModel = createViewModel()

        // Answer all 5 questions
        repeat(4) {
            viewModel.onAnswerChanged("answer-$it")
            viewModel.onNext()
        }
        viewModel.onAnswerChanged("last-answer")
        viewModel.onNext()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.recoveredPatientId)
        assertEquals("patient-123", state.recoveredPatientId)
        assertFalse(state.isLoading)
    }

    @Test
    fun `shows error on recovery failure`() = runTest {
        coEvery { recoverPatientSession(any()) } returns Result.Error(
            RuntimeException("Invalid answers"),
            "Invalid answers",
        )

        val viewModel = createViewModel()

        repeat(4) {
            viewModel.onAnswerChanged("answer-$it")
            viewModel.onNext()
        }
        viewModel.onAnswerChanged("last-answer")
        viewModel.onNext()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.recoveredPatientId)
        assertNotNull(state.error)
        verify { rateLimiter.recordAttempt() }
    }

    @Test
    fun `rate limited state is detected`() {
        every { rateLimiter.canAttempt() } returns false
        every { rateLimiter.remainingAttempts() } returns 0

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.isRateLimited)
        assertEquals(0, viewModel.uiState.value.remainingAttempts)
    }

    private fun createViewModel() = PatientRecoveryViewModel(
        recoverPatientSession = recoverPatientSession,
        rateLimiter = rateLimiter,
    )
}

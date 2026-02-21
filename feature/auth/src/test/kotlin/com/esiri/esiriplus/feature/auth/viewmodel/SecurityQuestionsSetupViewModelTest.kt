package com.esiri.esiriplus.feature.auth.viewmodel

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.usecase.SetupSecurityQuestionsUseCase
import io.mockk.coEvery
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityQuestionsSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var setupSecurityQuestions: SetupSecurityQuestionsUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        setupSecurityQuestions = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onNext advances question index`() {
        val viewModel = createViewModel()

        viewModel.onAnswerChanged("Dar es Salaam")
        viewModel.onNext()

        val state = viewModel.uiState.value
        assertEquals(1, state.currentQuestionIndex)
        assertEquals(1, state.answers.size)
    }

    @Test
    fun `submit succeeds and sets isComplete`() = runTest {
        coEvery { setupSecurityQuestions(any()) } returns Result.Success(Unit)

        val viewModel = createViewModel()

        repeat(4) {
            viewModel.onAnswerChanged("answer-$it")
            viewModel.onNext()
        }
        viewModel.onAnswerChanged("last-answer")
        viewModel.onNext()

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isComplete)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `submit failure shows error`() = runTest {
        coEvery { setupSecurityQuestions(any()) } returns Result.Error(
            RuntimeException("Server error"),
            "Server error",
        )

        val viewModel = createViewModel()

        repeat(4) {
            viewModel.onAnswerChanged("answer-$it")
            viewModel.onNext()
        }
        viewModel.onAnswerChanged("last-answer")
        viewModel.onNext()

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isComplete)
        val error = viewModel.uiState.value.error
        assertNotNull(error)
        assertTrue(error!!.isNotEmpty())
    }

    private fun createViewModel() = SecurityQuestionsSetupViewModel(
        setupSecurityQuestions = setupSecurityQuestions,
    )
}

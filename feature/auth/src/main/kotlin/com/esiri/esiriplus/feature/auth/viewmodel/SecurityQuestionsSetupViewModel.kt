package com.esiri.esiriplus.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.SecurityQuestions
import com.esiri.esiriplus.core.domain.usecase.SetupSecurityQuestionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityQuestionsSetupViewModel @Inject constructor(
    private val setupSecurityQuestions: SetupSecurityQuestionsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityQuestionsSetupUiState())
    val uiState: StateFlow<SecurityQuestionsSetupUiState> = _uiState.asStateFlow()

    fun onAnswerChanged(answer: String) {
        _uiState.update { it.copy(currentAnswer = answer, error = null) }
    }

    fun onNext() {
        val state = _uiState.value
        if (state.currentAnswer.isBlank()) return

        val questionKey = SecurityQuestions.ALL[state.currentQuestionIndex]
        val updatedAnswers = state.answers + (questionKey to state.currentAnswer.trim())

        if (state.currentQuestionIndex < SecurityQuestions.ALL.lastIndex) {
            _uiState.update {
                it.copy(
                    currentQuestionIndex = state.currentQuestionIndex + 1,
                    answers = updatedAnswers,
                    currentAnswer = "",
                    error = null,
                )
            }
        } else {
            submitAnswers(updatedAnswers)
        }
    }

    private fun submitAnswers(answers: Map<String, String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = setupSecurityQuestions(answers)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, isComplete = true) }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message ?: result.exception.message
                                ?: "Failed to save security questions.",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}

data class SecurityQuestionsSetupUiState(
    val currentQuestionIndex: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val currentAnswer: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,
)

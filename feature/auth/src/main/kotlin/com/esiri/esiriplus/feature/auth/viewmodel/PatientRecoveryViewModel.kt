package com.esiri.esiriplus.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.SecurityQuestions
import com.esiri.esiriplus.core.domain.usecase.RecoverPatientSessionUseCase
import com.esiri.esiriplus.feature.auth.recovery.RecoveryRateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientRecoveryViewModel @Inject constructor(
    private val recoverPatientSession: RecoverPatientSessionUseCase,
    private val rateLimiter: RecoveryRateLimiter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientRecoveryUiState())
    val uiState: StateFlow<PatientRecoveryUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                remainingAttempts = rateLimiter.remainingAttempts(),
                isRateLimited = !rateLimiter.canAttempt(),
            )
        }
    }

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
            submitRecovery(updatedAnswers)
        }
    }

    fun onContinueToDashboard() {
        _uiState.update { it.copy(continueClicked = true) }
    }

    private fun submitRecovery(answers: Map<String, String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            rateLimiter.recordAttempt()

            when (val result = recoverPatientSession(answers)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            recoveredPatientId = result.data.user.id,
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message ?: result.exception.message
                                ?: "Recovery failed. Please try again.",
                            remainingAttempts = rateLimiter.remainingAttempts(),
                            isRateLimited = !rateLimiter.canAttempt(),
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}

data class PatientRecoveryUiState(
    val currentQuestionIndex: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val currentAnswer: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val remainingAttempts: Int = 5,
    val isRateLimited: Boolean = false,
    val recoveredPatientId: String? = null,
    val continueClicked: Boolean = false,
)

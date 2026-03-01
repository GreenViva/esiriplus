package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.ConsultationSessionManager
import com.esiri.esiriplus.core.network.service.PaymentService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ExtensionPaymentUiState(
    val consultationId: String = "",
    val amount: Int = 0,
    val serviceType: String = "",
    val isLoading: Boolean = false,
    val paymentId: String? = null,
    val paymentStatus: PaymentStep = PaymentStep.CONFIRM,
    val errorMessage: String? = null,
)

@HiltViewModel
class ExtensionPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val paymentService: PaymentService,
    private val consultationSessionManager: ConsultationSessionManager,
) : ViewModel() {

    private val consultationId: String = savedStateHandle["consultationId"] ?: ""
    private val amount: Int = savedStateHandle["amount"] ?: 0
    private val serviceType: String = savedStateHandle["serviceType"] ?: ""

    private val _uiState = MutableStateFlow(
        ExtensionPaymentUiState(
            consultationId = consultationId,
            amount = amount,
            serviceType = serviceType,
        ),
    )
    val uiState: StateFlow<ExtensionPaymentUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private val mockPhoneNumber = "255700000000"

    fun initiatePayment() {
        val state = _uiState.value
        if (state.isLoading) return

        if (state.amount <= 0) {
            _uiState.update { it.copy(errorMessage = "Invalid payment amount") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val idempotencyKey = UUID.randomUUID().toString()

            val result = paymentService.initiateServicePayment(
                phoneNumber = mockPhoneNumber,
                amount = state.amount,
                serviceType = state.serviceType,
                consultationId = state.consultationId.ifBlank { null },
                idempotencyKey = idempotencyKey,
            )

            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            paymentId = result.data.paymentId,
                            paymentStatus = PaymentStep.PROCESSING,
                        )
                    }
                    startPolling(result.data.paymentId)
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                            paymentStatus = PaymentStep.FAILED,
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Network error. Please check your connection.",
                            paymentStatus = PaymentStep.FAILED,
                        )
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Session expired. Please log in again.",
                            paymentStatus = PaymentStep.FAILED,
                        )
                    }
                }
            }
        }
    }

    private fun startPolling(paymentId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            repeat(40) {
                delay(3000)
                val payment = paymentService.getPaymentStatus(paymentId)
                if (payment != null) {
                    when (payment.status.lowercase()) {
                        "completed" -> {
                            _uiState.update {
                                it.copy(paymentStatus = PaymentStep.COMPLETED)
                            }
                            // Notify session manager that payment succeeded
                            consultationSessionManager.paymentConfirmed(paymentId)
                            return@launch
                        }
                        "failed" -> {
                            _uiState.update {
                                it.copy(
                                    paymentStatus = PaymentStep.FAILED,
                                    errorMessage = payment.failureReason ?: "Payment failed",
                                )
                            }
                            return@launch
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(
                    paymentStatus = PaymentStep.FAILED,
                    errorMessage = "Payment timed out. Please try again.",
                )
            }
        }
    }

    fun retryPayment() {
        pollingJob?.cancel()
        _uiState.update {
            it.copy(
                paymentId = null,
                paymentStatus = PaymentStep.CONFIRM,
                errorMessage = null,
                isLoading = false,
            )
        }
    }

    fun cancelExtensionPayment() {
        pollingJob?.cancel()
        viewModelScope.launch {
            try {
                consultationSessionManager.cancelPayment()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel extension payment", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    companion object {
        private const val TAG = "ExtensionPaymentVM"
    }
}

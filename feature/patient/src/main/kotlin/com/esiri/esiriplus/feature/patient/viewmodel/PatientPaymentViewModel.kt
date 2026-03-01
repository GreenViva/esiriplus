package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.model.ApiResult
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

data class PaymentUiState(
    val consultationId: String = "",
    val amount: Int = 0,
    val serviceType: String = "",
    val isLoading: Boolean = false,
    val paymentId: String? = null,
    val paymentStatus: PaymentStep = PaymentStep.CONFIRM,
    val errorMessage: String? = null,
)

enum class PaymentStep {
    /** Show amount + "Pay" button */
    CONFIRM,
    /** STK push sent, waiting for mock callback */
    PROCESSING,
    /** Payment completed */
    COMPLETED,
    /** Payment failed or timed out */
    FAILED,
}

@HiltViewModel
class PatientPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val paymentService: PaymentService,
) : ViewModel() {

    private val consultationId: String = savedStateHandle["consultationId"] ?: ""

    private val _uiState = MutableStateFlow(PaymentUiState(consultationId = consultationId))
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    // Dummy phone number for mock mode — no real number is collected from the patient.
    // When switching to real M-Pesa, this will be replaced with a different flow
    // (e.g. USSD-initiated payment or user-provided number at that point).
    private val mockPhoneNumber = "255700000000"

    init {
        loadConsultationDetails()
    }

    private fun loadConsultationDetails() {
        // Default values — in a full implementation this would fetch the
        // consultation's service tier and price from the backend.
        _uiState.update {
            it.copy(
                amount = 1200,
                serviceType = "gp",
            )
        }
    }

    fun updateServiceDetails(serviceType: String, amount: Int) {
        _uiState.update { it.copy(serviceType = serviceType, amount = amount) }
    }

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
            // Poll every 3 seconds for up to 2 minutes
            repeat(40) {
                delay(3000)
                val payment = paymentService.getPaymentStatus(paymentId)
                if (payment != null) {
                    when (payment.status.lowercase()) {
                        "completed" -> {
                            _uiState.update {
                                it.copy(paymentStatus = PaymentStep.COMPLETED)
                            }
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
            // Timeout
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

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

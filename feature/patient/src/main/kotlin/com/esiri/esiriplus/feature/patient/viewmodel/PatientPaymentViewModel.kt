package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.PaymentDao
import com.esiri.esiriplus.core.database.entity.PaymentEntity
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.PaymentService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

// TODO: Localize hardcoded user-facing strings (error messages).
//  Inject Application context and use context.getString(R.string.xxx) from feature.patient.R
@HiltViewModel
class PatientPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val paymentService: PaymentService,
    private val paymentDao: PaymentDao,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val consultationId: String = savedStateHandle["consultationId"] ?: ""
    private val routeAmount: Int = savedStateHandle["amount"] ?: 0
    private val routeServiceType: String = savedStateHandle["serviceType"] ?: ""

    private val _uiState = MutableStateFlow(PaymentUiState(consultationId = consultationId))
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    private var patientPhone = "255700000000"

    init {
        loadPatientPhone()
        checkExistingPayment()
    }

    private fun loadPatientPhone() {
        viewModelScope.launch {
            try {
                val session = authRepository.currentSession.first()
                val phone = session?.user?.phone
                if (!phone.isNullOrBlank()) {
                    patientPhone = phone
                }
            } catch (_: Exception) {
                // Keep default phone
            }
        }
    }

    private fun checkExistingPayment() {
        if (consultationId.isBlank()) {
            loadConsultationDetails()
            return
        }
        viewModelScope.launch {
            val existing = paymentDao.getCompletedByConsultationId(consultationId)
            if (existing != null) {
                Log.d(TAG, "Found existing completed payment for consultation=$consultationId")
                _uiState.update { it.copy(paymentStatus = PaymentStep.COMPLETED) }
            } else {
                loadConsultationDetails()
            }
        }
    }

    private fun loadConsultationDetails() {
        _uiState.update {
            it.copy(
                amount = if (routeAmount > 0) routeAmount else 0,
                serviceType = routeServiceType.ifBlank { "gp" },
            )
        }
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
                phoneNumber = patientPhone,
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
                when (val result = paymentService.getPaymentStatus(paymentId)) {
                    is ApiResult.Success -> {
                        val payment = result.data
                        when (payment.status.lowercase()) {
                            "completed" -> {
                                _uiState.update {
                                    it.copy(paymentStatus = PaymentStep.COMPLETED)
                                }
                                persistCompletedPayment(paymentId, payment.transactionId)
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
                    else -> { /* continue polling */ }
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

    private fun persistCompletedPayment(paymentId: String, transactionId: String?) {
        viewModelScope.launch {
            try {
                val sessionId = authRepository.currentSession.first()?.user?.id ?: ""
                val now = System.currentTimeMillis()
                val entity = PaymentEntity(
                    paymentId = paymentId,
                    patientSessionId = sessionId,
                    amount = _uiState.value.amount,
                    paymentMethod = "MPESA",
                    transactionId = transactionId,
                    phoneNumber = patientPhone,
                    status = "completed",
                    createdAt = now,
                    updatedAt = now,
                    consultationId = consultationId,
                )
                paymentDao.insert(entity)
                Log.d(TAG, "Persisted completed payment: $paymentId for consultation=$consultationId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist payment locally", e)
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

    companion object {
        private const val TAG = "PatientPaymentVM"
    }
}

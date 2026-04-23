package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.validation.InputValidators
import com.esiri.esiriplus.core.database.dao.PaymentDao
import com.esiri.esiriplus.core.database.entity.PaymentEntity
import com.esiri.esiriplus.core.domain.model.PaymentMethod
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.PaymentService
import com.esiri.esiriplus.feature.patient.R
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
    /** Selected method for this attempt. Persisted across retries. */
    val paymentMethod: PaymentMethod = PaymentMethod.MPESA,
    /** Mobile-number flow only: the phone the user typed. */
    val phoneNumberInput: String = "",
)

enum class PaymentStep {
    /** Show amount + method chooser */
    CONFIRM,
    /** MOBILE_NUMBER only: user typing their phone before we ask the provider to push its prompt */
    PHONE_ENTRY,
    /** MPESA STK wait, or MOBILE_NUMBER provider wallet-prompt wait */
    PROCESSING,
    /** Payment completed */
    COMPLETED,
    /** Payment failed or timed out */
    FAILED,
}

@HiltViewModel
class PatientPaymentViewModel @Inject constructor(
    private val application: Application,
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

    fun selectMethod(method: PaymentMethod) {
        _uiState.update { it.copy(paymentMethod = method, errorMessage = null) }
    }

    /**
     * Triggered by the "Pay" button on the CONFIRM screen. Branches on the
     * selected method:
     *  - MPESA → classic STK push (existing flow)
     *  - MOBILE_NUMBER → jump to PHONE_ENTRY; the actual initiate call
     *    happens once the user submits the phone.
     */
    fun onPayClicked() {
        when (_uiState.value.paymentMethod) {
            PaymentMethod.MPESA -> initiatePayment()
            PaymentMethod.MOBILE_NUMBER -> _uiState.update {
                it.copy(
                    paymentStatus = PaymentStep.PHONE_ENTRY,
                    phoneNumberInput = patientPhone.takeIf { p -> p != "255700000000" }.orEmpty(),
                    errorMessage = null,
                )
            }
        }
    }

    fun onPhoneInputChanged(phone: String) {
        _uiState.update {
            it.copy(phoneNumberInput = phone.filter { c -> c.isDigit() }.take(12))
        }
    }

    /**
     * Mobile-number flow: hit initiate-mobile-payment with the typed phone
     * number. The server creates the payments row and asks the provider to
     * push its wallet prompt to the user's device. The user confirms on the
     * provider's own UI with their wallet PIN; we poll [getPaymentStatus]
     * like we do for STK.
     */
    fun submitPhoneNumber() {
        val state = _uiState.value
        if (state.isLoading) return

        val phone = state.phoneNumberInput
        if (!phone.matches(Regex("^2556\\d{8}\$|^2557\\d{8}\$"))) {
            _uiState.update {
                it.copy(errorMessage = application.getString(R.string.payment_phone_invalid))
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val idempotencyKey = UUID.randomUUID().toString()
            val result = paymentService.initiateMobilePayment(
                phoneNumber = phone,
                amount = state.amount,
                paymentType = "service_access",
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
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = application.getString(R.string.vm_network_error),
                    )
                }
                is ApiResult.Unauthorized -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = application.getString(R.string.vm_session_expired),
                    )
                }
            }
        }
    }

    fun initiatePayment() {
        val state = _uiState.value
        if (state.isLoading) return

        val amountValidation = InputValidators.validatePaymentAmount(state.amount)
        if (!amountValidation.isValid) {
            _uiState.update { it.copy(errorMessage = amountValidation.errorMessage) }
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
                            errorMessage = application.getString(R.string.vm_network_error),
                            paymentStatus = PaymentStep.FAILED,
                        )
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = application.getString(R.string.vm_session_expired),
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
                                        errorMessage = payment.failureReason ?: application.getString(R.string.vm_payment_failed),
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
                    errorMessage = application.getString(R.string.vm_payment_timed_out),
                )
            }
        }
    }

    private fun persistCompletedPayment(paymentId: String, transactionId: String?) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val sessionId = authRepository.currentSession.first()?.user?.id ?: ""
                val now = System.currentTimeMillis()
                val phoneForRecord = when (state.paymentMethod) {
                    PaymentMethod.MOBILE_NUMBER -> state.phoneNumberInput.ifBlank { patientPhone }
                    PaymentMethod.MPESA -> patientPhone
                }
                val entity = PaymentEntity(
                    paymentId = paymentId,
                    patientSessionId = sessionId,
                    amount = state.amount,
                    paymentMethod = state.paymentMethod.name,
                    transactionId = transactionId,
                    phoneNumber = phoneForRecord,
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

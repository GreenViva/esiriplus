package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.esiri.esiriplus.core.database.dao.PaymentDao
import com.esiri.esiriplus.core.database.entity.PaymentEntity
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.PaymentService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PatientPaymentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var paymentService: PaymentService
    private lateinit var paymentDao: PaymentDao
    private lateinit var authRepository: AuthRepository

    private val testUser = User(
        id = "u1",
        fullName = "Test Patient",
        phone = "+255700000000",
        role = UserRole.PATIENT,
    )

    private val testSession = Session(
        accessToken = "token",
        refreshToken = "refresh",
        expiresAt = Instant.now().plusSeconds(3600),
        user = testUser,
        createdAt = Instant.now(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        paymentService = mockk(relaxed = true)
        paymentDao = mockk(relaxed = true)
        authRepository = mockk()

        every { authRepository.currentSession } returns flowOf(testSession)
        coEvery { paymentDao.getCompletedByConsultationId(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads consultation details from saved state handle`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("c1", state.consultationId)
        assertEquals(5000, state.amount)
        assertEquals("gp", state.serviceType)
    }

    @Test
    fun `initial state sets payment status to CONFIRM`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(PaymentStep.CONFIRM, viewModel.uiState.value.paymentStatus)
    }

    @Test
    fun `initiatePayment sets status to PROCESSING on success`() = runTest {
        val stkPushResponse = mockk<Any>(relaxed = true)
        every { (stkPushResponse as Any).toString() } returns "StkPushResponse"

        coEvery {
            paymentService.initiateServicePayment(
                phoneNumber = any(),
                amount = any(),
                serviceType = any(),
                consultationId = any(),
                idempotencyKey = any(),
            )
        } returns ApiResult.Success(mockk(relaxed = true) {
            every { paymentId } returns "pay-123"
        })

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.initiatePayment()
        // Only advance enough for the API call to complete, not through polling delays
        advanceTimeBy(100)

        val state = viewModel.uiState.value
        assertEquals(PaymentStep.PROCESSING, state.paymentStatus)
        assertEquals("pay-123", state.paymentId)
    }

    @Test
    fun `initiatePayment shows error on API failure`() = runTest {
        coEvery {
            paymentService.initiateServicePayment(
                phoneNumber = any(),
                amount = any(),
                serviceType = any(),
                consultationId = any(),
                idempotencyKey = any(),
            )
        } returns ApiResult.Error(500, "Server error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.initiatePayment()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PaymentStep.FAILED, state.paymentStatus)
        assertEquals("Server error", state.errorMessage)
    }

    @Test
    fun `initiatePayment validates amount greater than zero`() = runTest {
        val viewModel = createViewModel(amount = 0)
        advanceUntilIdle()

        viewModel.initiatePayment()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertEquals(PaymentStep.CONFIRM, state.paymentStatus)
    }

    @Test
    fun `existing completed payment skips to COMPLETED step`() = runTest {
        val existingPayment = PaymentEntity(
            paymentId = "pay-existing",
            patientSessionId = "u1",
            amount = 5000,
            paymentMethod = "MPESA",
            transactionId = "txn-123",
            phoneNumber = "+255700000000",
            status = "completed",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            consultationId = "c1",
        )
        coEvery { paymentDao.getCompletedByConsultationId("c1") } returns existingPayment

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(PaymentStep.COMPLETED, viewModel.uiState.value.paymentStatus)
    }

    @Test
    fun `retryPayment resets state to CONFIRM`() = runTest {
        coEvery {
            paymentService.initiateServicePayment(
                phoneNumber = any(),
                amount = any(),
                serviceType = any(),
                consultationId = any(),
                idempotencyKey = any(),
            )
        } returns ApiResult.Error(500, "Server error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.initiatePayment()
        advanceUntilIdle()
        assertEquals(PaymentStep.FAILED, viewModel.uiState.value.paymentStatus)

        viewModel.retryPayment()

        val state = viewModel.uiState.value
        assertEquals(PaymentStep.CONFIRM, state.paymentStatus)
        assertNull(state.errorMessage)
        assertNull(state.paymentId)
    }

    @Test
    fun `dismissError clears error message`() = runTest {
        coEvery {
            paymentService.initiateServicePayment(
                phoneNumber = any(),
                amount = any(),
                serviceType = any(),
                consultationId = any(),
                idempotencyKey = any(),
            )
        } returns ApiResult.Error(500, "Server error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.initiatePayment()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `initiatePayment handles network error`() = runTest {
        coEvery {
            paymentService.initiateServicePayment(
                phoneNumber = any(),
                amount = any(),
                serviceType = any(),
                consultationId = any(),
                idempotencyKey = any(),
            )
        } returns ApiResult.NetworkError(
            exception = RuntimeException("No internet"),
            message = "Network error",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.initiatePayment()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PaymentStep.FAILED, state.paymentStatus)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `initiatePayment handles unauthorized error`() = runTest {
        coEvery {
            paymentService.initiateServicePayment(
                phoneNumber = any(),
                amount = any(),
                serviceType = any(),
                consultationId = any(),
                idempotencyKey = any(),
            )
        } returns ApiResult.Unauthorized

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.initiatePayment()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PaymentStep.FAILED, state.paymentStatus)
        assertNotNull(state.errorMessage)
    }

    private fun createViewModel(
        consultationId: String = "c1",
        amount: Int = 5000,
        serviceType: String = "gp",
    ): PatientPaymentViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "consultationId" to consultationId,
                "amount" to amount,
                "serviceType" to serviceType,
            ),
        )
        return PatientPaymentViewModel(
            application = application,
            savedStateHandle = savedStateHandle,
            paymentService = paymentService,
            paymentDao = paymentDao,
            authRepository = authRepository,
        )
    }
}

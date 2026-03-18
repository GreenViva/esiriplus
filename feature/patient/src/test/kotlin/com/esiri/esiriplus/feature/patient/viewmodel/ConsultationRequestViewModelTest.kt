package com.esiri.esiriplus.feature.patient.viewmodel

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.dao.PatientProfileDao
import com.esiri.esiriplus.core.database.entity.PatientProfileEntity
import com.esiri.esiriplus.core.domain.model.ConsultationRequest
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.ConsultationRequestRepository
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.service.ConsultationRequestRealtimeService
import com.esiri.esiriplus.core.network.service.RequestRealtimeEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class ConsultationRequestViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var consultationRequestRepository: ConsultationRequestRepository
    private lateinit var realtimeService: ConsultationRequestRealtimeService
    private lateinit var tokenManager: TokenManager
    private lateinit var supabaseClientProvider: SupabaseClientProvider
    private lateinit var patientProfileDao: PatientProfileDao
    private lateinit var authRepository: AuthRepository

    private val sessionFlow = MutableStateFlow<Session?>(null)
    private val profileFlow = MutableStateFlow<PatientProfileEntity?>(null)
    private val realtimeEventsFlow = MutableSharedFlow<RequestRealtimeEvent>()

    private val testSession = Session(
        accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJzZXNzaW9uX2lkIjoicHMxMjMifQ.sig",
        refreshToken = "refresh",
        expiresAt = Instant.now().plusSeconds(3600),
        user = User(
            id = "u1",
            role = UserRole.PATIENT,
            fullName = "Test",
            email = null,
            phone = "+255700000000",
        ),
        createdAt = Instant.now(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        consultationRequestRepository = mockk(relaxed = true)
        realtimeService = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
        supabaseClientProvider = mockk(relaxed = true)
        patientProfileDao = mockk(relaxed = true)
        authRepository = mockk()

        every { authRepository.currentSession } returns sessionFlow
        every { patientProfileDao.getByUserId(any()) } returns profileFlow
        every { realtimeService.requestEvents } returns realtimeEventsFlow
        every { tokenManager.getAccessTokenSync() } returns testSession.accessToken
        every { tokenManager.getRefreshTokenSync() } returns testSession.refreshToken

        // Mock checkRequestStatus for polling (returns pending by default)
        coEvery {
            consultationRequestRepository.checkRequestStatus(any())
        } returns Result.Success(makeConsultationRequest(status = ConsultationRequestStatus.PENDING))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    fun `initial state has no active request`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.activeRequestId)
        assertNull(state.status)
        assertNull(state.statusMessage)
        assertNull(state.errorMessage)
        assertFalse(state.isSending)
        assertFalse(state.showSymptomsDialog)
        assertEquals(0, state.secondsRemaining)
    }

    @Test
    fun `requestConsultation shows symptoms dialog`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.requestConsultation(doctorId = "doc1", serviceType = "gp")

        val state = vm.uiState.value
        assertTrue(state.showSymptomsDialog)
        assertEquals("doc1", state.pendingDoctorId)
        assertEquals("gp", state.pendingServiceType)
    }

    @Test
    fun `dismissSymptomsDialog hides dialog`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.requestConsultation(doctorId = "doc1", serviceType = "gp")
        assertTrue(vm.uiState.value.showSymptomsDialog)

        vm.dismissSymptomsDialog()

        val state = vm.uiState.value
        assertFalse(state.showSymptomsDialog)
        assertNull(state.pendingDoctorId)
        assertNull(state.pendingServiceType)
    }

    @Test
    fun `sendRequest sets isSending to true then creates request`() = runTest {
        val request = makeConsultationRequest(requestId = "req1")
        coEvery {
            consultationRequestRepository.createRequest(
                doctorId = any(),
                serviceType = any(),
                consultationType = any(),
                chiefComplaint = any(),
                symptoms = any(),
                patientAgeGroup = any(),
                patientSex = any(),
                patientBloodGroup = any(),
                patientAllergies = any(),
                patientChronicConditions = any(),
            )
        } returns Result.Success(request)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendRequest(doctorId = "doc1", serviceType = "gp")

        // Before coroutine completes, isSending should be true
        assertTrue(vm.uiState.value.isSending)

        // Advance just enough for the API call, not through countdown/polling
        advanceTimeBy(100)

        assertFalse(vm.uiState.value.isSending)
        assertEquals("req1", vm.uiState.value.activeRequestId)
    }

    @Test
    fun `sendRequest on success sets status to PENDING`() = runTest {
        val request = makeConsultationRequest(requestId = "req1")
        coEvery {
            consultationRequestRepository.createRequest(
                doctorId = any(),
                serviceType = any(),
                consultationType = any(),
                chiefComplaint = any(),
                symptoms = any(),
                patientAgeGroup = any(),
                patientSex = any(),
                patientBloodGroup = any(),
                patientAllergies = any(),
                patientChronicConditions = any(),
            )
        } returns Result.Success(request)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendRequest(doctorId = "doc1", serviceType = "gp")
        // Advance past the API call but not through the full countdown
        advanceTimeBy(100)

        val state = vm.uiState.value
        assertEquals(ConsultationRequestStatus.PENDING, state.status)
        assertEquals("req1", state.activeRequestId)
        assertEquals("doc1", state.activeRequestDoctorId)
        assertNotNull(state.statusMessage)
        assertEquals(ConsultationRequestViewModel.REQUEST_TTL_SECONDS, state.secondsRemaining)
    }

    @Test
    fun `sendRequest on error shows error message`() = runTest {
        coEvery {
            consultationRequestRepository.createRequest(
                doctorId = any(),
                serviceType = any(),
                consultationType = any(),
                chiefComplaint = any(),
                symptoms = any(),
                patientAgeGroup = any(),
                patientSex = any(),
                patientBloodGroup = any(),
                patientAllergies = any(),
                patientChronicConditions = any(),
            )
        } returns Result.Error(exception = RuntimeException("Network error"), message = "Network error")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendRequest(doctorId = "doc1", serviceType = "gp")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isSending)
        assertNull(state.activeRequestId)
        assertNull(state.activeRequestDoctorId)
        assertEquals("Network error", state.errorMessage)
    }

    @Test
    fun `sendRequest prevents duplicate when already sending`() = runTest {
        val request = makeConsultationRequest(requestId = "req1")
        coEvery {
            consultationRequestRepository.createRequest(
                doctorId = any(),
                serviceType = any(),
                consultationType = any(),
                chiefComplaint = any(),
                symptoms = any(),
                patientAgeGroup = any(),
                patientSex = any(),
                patientBloodGroup = any(),
                patientAllergies = any(),
                patientChronicConditions = any(),
            )
        } returns Result.Success(request)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendRequest(doctorId = "doc1", serviceType = "gp")
        assertTrue(vm.uiState.value.isSending)

        // Second call should be a no-op
        vm.sendRequest(doctorId = "doc2", serviceType = "specialist")

        advanceTimeBy(100)

        assertEquals("req1", vm.uiState.value.activeRequestId)
        assertEquals("doc1", vm.uiState.value.activeRequestDoctorId)
    }

    @Test
    fun `sendRequest prevents duplicate when active request exists`() = runTest {
        val request = makeConsultationRequest(requestId = "req1")
        coEvery {
            consultationRequestRepository.createRequest(
                doctorId = any(),
                serviceType = any(),
                consultationType = any(),
                chiefComplaint = any(),
                symptoms = any(),
                patientAgeGroup = any(),
                patientSex = any(),
                patientBloodGroup = any(),
                patientAllergies = any(),
                patientChronicConditions = any(),
            )
        } returns Result.Success(request)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendRequest(doctorId = "doc1", serviceType = "gp")
        advanceTimeBy(100)
        assertEquals("req1", vm.uiState.value.activeRequestId)

        // Second call is blocked because activeRequestId is set
        vm.sendRequest(doctorId = "doc2", serviceType = "specialist")
        advanceTimeBy(100)

        assertEquals("req1", vm.uiState.value.activeRequestId)
        assertEquals("doc1", vm.uiState.value.activeRequestDoctorId)
    }

    @Test
    fun `dismissError clears error message`() = runTest {
        coEvery {
            consultationRequestRepository.createRequest(
                doctorId = any(),
                serviceType = any(),
                consultationType = any(),
                chiefComplaint = any(),
                symptoms = any(),
                patientAgeGroup = any(),
                patientSex = any(),
                patientBloodGroup = any(),
                patientAllergies = any(),
                patientChronicConditions = any(),
            )
        } returns Result.Error(exception = RuntimeException("fail"), message = "fail")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendRequest(doctorId = "doc1", serviceType = "gp")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.errorMessage)

        vm.dismissError()

        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `dismissStatus clears request state`() = runTest {
        val request = makeConsultationRequest(requestId = "req1")
        coEvery {
            consultationRequestRepository.createRequest(
                doctorId = any(),
                serviceType = any(),
                consultationType = any(),
                chiefComplaint = any(),
                symptoms = any(),
                patientAgeGroup = any(),
                patientSex = any(),
                patientBloodGroup = any(),
                patientAllergies = any(),
                patientChronicConditions = any(),
            )
        } returns Result.Success(request)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendRequest(doctorId = "doc1", serviceType = "gp")
        advanceTimeBy(100)
        assertNotNull(vm.uiState.value.activeRequestId)

        vm.dismissStatus()

        val state = vm.uiState.value
        assertNull(state.activeRequestId)
        assertNull(state.activeRequestDoctorId)
        assertNull(state.status)
        assertNull(state.statusMessage)
        assertNull(state.errorMessage)
        assertFalse(state.isSending)
        assertEquals(0, state.secondsRemaining)
    }

    @Test
    fun `confirmAndSendRequest closes dialog and sends request`() = runTest {
        val request = makeConsultationRequest(requestId = "req1")
        coEvery {
            consultationRequestRepository.createRequest(
                doctorId = any(),
                serviceType = any(),
                consultationType = any(),
                chiefComplaint = any(),
                symptoms = any(),
                patientAgeGroup = any(),
                patientSex = any(),
                patientBloodGroup = any(),
                patientAllergies = any(),
                patientChronicConditions = any(),
            )
        } returns Result.Success(request)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.requestConsultation(doctorId = "doc1", serviceType = "gp")
        assertTrue(vm.uiState.value.showSymptomsDialog)

        vm.confirmAndSendRequest("headache and fever")
        advanceTimeBy(100)

        val state = vm.uiState.value
        assertFalse(state.showSymptomsDialog)
        assertNull(state.pendingDoctorId)
        assertNull(state.pendingServiceType)
        assertEquals("req1", state.activeRequestId)
        assertEquals(ConsultationRequestStatus.PENDING, state.status)
    }

    @Test
    fun `requestConsultation is no-op when already sending`() = runTest {
        val request = makeConsultationRequest(requestId = "req1")
        coEvery {
            consultationRequestRepository.createRequest(
                doctorId = any(),
                serviceType = any(),
                consultationType = any(),
                chiefComplaint = any(),
                symptoms = any(),
                patientAgeGroup = any(),
                patientSex = any(),
                patientBloodGroup = any(),
                patientAllergies = any(),
                patientChronicConditions = any(),
            )
        } returns Result.Success(request)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendRequest(doctorId = "doc1", serviceType = "gp")
        assertTrue(vm.uiState.value.isSending)

        vm.requestConsultation(doctorId = "doc2", serviceType = "specialist")
        assertFalse(vm.uiState.value.showSymptomsDialog)

        advanceTimeBy(100)
    }

    @Test
    fun `patient profile is loaded from dao on init`() = runTest {
        sessionFlow.value = testSession
        profileFlow.value = PatientProfileEntity(
            id = "pp1",
            userId = "u1",
            ageGroup = "25-34",
            sex = "Male",
            bloodGroup = "O+",
            allergies = "Penicillin",
            chronicConditions = "Asthma",
        )

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("25-34", state.patientAgeGroup)
        assertEquals("Male", state.patientSex)
        assertEquals("O+", state.patientBloodGroup)
        assertEquals("Penicillin", state.patientAllergies)
        assertEquals("Asthma", state.patientChronicConditions)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun createViewModel(): ConsultationRequestViewModel =
        ConsultationRequestViewModel(
            consultationRequestRepository = consultationRequestRepository,
            realtimeService = realtimeService,
            tokenManager = tokenManager,
            supabaseClientProvider = supabaseClientProvider,
            patientProfileDao = patientProfileDao,
            authRepository = authRepository,
        )

    private fun makeConsultationRequest(
        requestId: String = "req1",
        doctorId: String = "doc1",
        status: ConsultationRequestStatus = ConsultationRequestStatus.PENDING,
        consultationId: String? = null,
    ): ConsultationRequest = ConsultationRequest(
        requestId = requestId,
        patientSessionId = "ps123",
        doctorId = doctorId,
        serviceType = "gp",
        status = status,
        createdAt = System.currentTimeMillis(),
        expiresAt = System.currentTimeMillis() + 60_000,
        consultationId = consultationId,
    )
}

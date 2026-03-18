package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.domain.model.DoctorRating
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.DoctorRatingRepository
import com.esiri.esiriplus.core.domain.usecase.LogoutUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PatientHomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var authRepository: AuthRepository
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var consultationDao: ConsultationDao
    private lateinit var doctorRatingRepository: DoctorRatingRepository

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var sessionFlow: MutableStateFlow<Session?>
    private lateinit var activeConsultationFlow: MutableStateFlow<ConsultationEntity?>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockPrefs = mockk<SharedPreferences>(relaxed = true)
        mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)

        application = mockk(relaxed = true)
        every { application.getSharedPreferences("patient_prefs", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.getBoolean("sounds_enabled", true) } returns true
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor

        sessionFlow = MutableStateFlow(null)
        activeConsultationFlow = MutableStateFlow(null)

        authRepository = mockk()
        every { authRepository.currentSession } returns sessionFlow

        logoutUseCase = mockk(relaxed = true)

        consultationDao = mockk(relaxed = true)
        every { consultationDao.getActiveConsultation() } returns activeConsultationFlow
        coEvery { consultationDao.getUnratedCompletedConsultation() } returns null

        doctorRatingRepository = mockk(relaxed = true)
        coEvery { doctorRatingRepository.getUnsyncedRatings() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has isLoading true`() = runTest {
        val viewModel = createViewModel()

        val initialState = viewModel.uiState.value
        assertTrue(initialState.isLoading)
        assertEquals("", initialState.patientId)
    }

    @Test
    fun `uiState emits patient ID from session`() = runTest {
        val viewModel = createViewModel()
        // Subscribe to trigger WhileSubscribed stateIn
        val job = launch { viewModel.uiState.collect {} }

        sessionFlow.value = createTestSession(userId = "ESR-ABCDEF-P8FP")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ESR-ABCDEF-P8FP", state.patientId)
        assertFalse(state.isLoading)
        job.cancel()
    }

    @Test
    fun `patient ID is properly masked`() = runTest {
        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }

        sessionFlow.value = createTestSession(userId = "ESR-ABCDEF-P8FP")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ESR-******-P8FP", state.maskedPatientId)
        job.cancel()
    }

    @Test
    fun `maskPatientId masks middle sections of hyphen-separated ID`() {
        assertEquals("ESR-******-P8FP", PatientHomeViewModel.maskPatientId("ESR-ABCDEF-P8FP"))
        assertEquals("A-**-***-D", PatientHomeViewModel.maskPatientId("A-BC-DEF-D"))
        assertEquals("FIRST-***-****-LAST", PatientHomeViewModel.maskPatientId("FIRST-MID-MID2-LAST"))
    }

    @Test
    fun `maskPatientId with fewer than three parts returns partially masked`() {
        val shortId = "AB"
        assertEquals("AB", PatientHomeViewModel.maskPatientId(shortId))

        val longNoHyphens = "ABCDEFGHIJ"
        assertEquals("ABC***GHIJ", PatientHomeViewModel.maskPatientId(longNoHyphens))
    }

    @Test
    fun `logout invokes logout use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        coVerify(exactly = 1) { logoutUseCase() }
    }

    @Test
    fun `toggleSounds updates sound preference`() = runTest {
        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }

        sessionFlow.value = createTestSession()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.soundsEnabled)

        viewModel.toggleSounds()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.soundsEnabled)
        verify { mockEditor.putBoolean("sounds_enabled", false) }
        verify { mockEditor.apply() }

        viewModel.toggleSounds()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.soundsEnabled)
        verify { mockEditor.putBoolean("sounds_enabled", true) }
        job.cancel()
    }

    @Test
    fun `active consultation is reflected in state`() = runTest {
        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }

        sessionFlow.value = createTestSession()
        val consultation = createTestConsultation()
        activeConsultationFlow.value = consultation
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(consultation, state.activeConsultation)
        assertEquals("consult-001", state.activeConsultation?.consultationId)
        job.cancel()
    }

    @Test
    fun `null session results in empty patient ID`() = runTest {
        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }

        sessionFlow.value = null
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.patientId)
        assertEquals("", state.maskedPatientId)
        assertFalse(state.isLoading)
        job.cancel()
    }

    @Test
    fun `pending rating consultation is loaded from dao on init`() = runTest {
        val unratedConsultation = createTestConsultation(
            consultationId = "consult-unrated",
            status = "completed",
        )
        coEvery { consultationDao.getUnratedCompletedConsultation() } returns unratedConsultation

        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }

        sessionFlow.value = createTestSession()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(unratedConsultation, state.pendingRatingConsultation)
        job.cancel()
    }

    @Test
    fun `unsynced ratings are synced on init`() = runTest {
        val unsyncedRating = DoctorRating(
            ratingId = "rating-1",
            doctorId = "doc-1",
            consultationId = "consult-001",
            patientSessionId = "patient-1",
            rating = 5,
            comment = "Great doctor",
            createdAt = System.currentTimeMillis(),
            synced = false,
        )
        coEvery { doctorRatingRepository.getUnsyncedRatings() } returns listOf(unsyncedRating)
        coEvery { doctorRatingRepository.submitRatingToServer(unsyncedRating) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { doctorRatingRepository.submitRatingToServer(unsyncedRating) }
        coVerify(exactly = 1) { doctorRatingRepository.markSynced("rating-1") }
    }

    @Test
    fun `dismissPendingRating clears pending rating`() = runTest {
        val unratedConsultation = createTestConsultation(status = "completed")
        coEvery { consultationDao.getUnratedCompletedConsultation() } returns unratedConsultation

        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }

        sessionFlow.value = createTestSession()
        advanceUntilIdle()

        assertEquals(unratedConsultation, viewModel.uiState.value.pendingRatingConsultation)

        viewModel.dismissPendingRating()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingRatingConsultation)
        job.cancel()
    }

    // ── Factory methods ──────────────────────────────────────────────────

    private fun createViewModel(): PatientHomeViewModel {
        return PatientHomeViewModel(
            application = application,
            authRepository = authRepository,
            logoutUseCase = logoutUseCase,
            consultationDao = consultationDao,
            doctorRatingRepository = doctorRatingRepository,
        )
    }

    private fun createTestSession(
        userId: String = "ESR-TEST01-X1Y2",
        fullName: String = "Test Patient",
    ): Session {
        return Session(
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
            expiresAt = Instant.now().plusSeconds(3600),
            user = User(
                id = userId,
                fullName = fullName,
                phone = "+255700000000",
                email = "test@example.com",
                role = UserRole.PATIENT,
            ),
        )
    }

    private fun createTestConsultation(
        consultationId: String = "consult-001",
        status: String = "active",
    ): ConsultationEntity {
        val now = System.currentTimeMillis()
        return ConsultationEntity(
            consultationId = consultationId,
            patientSessionId = "patient-session-1",
            doctorId = "doctor-1",
            status = status,
            serviceType = "general",
            consultationFee = 5000,
            requestExpiresAt = now + 3600_000,
            createdAt = now,
            updatedAt = now,
        )
    }
}

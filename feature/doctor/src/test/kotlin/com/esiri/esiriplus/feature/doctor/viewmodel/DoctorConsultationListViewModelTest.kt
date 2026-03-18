package com.esiri.esiriplus.feature.doctor.viewmodel

import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.ConsultationRow
import com.esiri.esiriplus.core.network.service.DoctorConsultationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class DoctorConsultationListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var consultationDao: ConsultationDao
    private lateinit var consultationService: DoctorConsultationService

    private val sessionFlow = MutableStateFlow<Session?>(null)
    private val consultationsFlow = MutableStateFlow<List<ConsultationEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        authRepository = mockk()
        consultationDao = mockk(relaxed = true)
        consultationService = mockk(relaxed = true)

        every { authRepository.currentSession } returns sessionFlow
        every { consultationDao.getByDoctorId("doc1") } returns consultationsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has isLoading true`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertEquals(emptyList<ConsultationEntity>(), state.consultations)
        assertNull(state.errorMessage)
    }

    @Test
    fun `consultations loaded from Room when session available`() = runTest {
        val entities = listOf(
            makeConsultationEntity("c1", createdAt = 3000L),
            makeConsultationEntity("c2", createdAt = 1000L),
        )
        consultationsFlow.value = entities
        sessionFlow.value = makeSession()

        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.consultations.size)
        // Sorted descending by createdAt: c1 (3000) then c2 (1000)
        assertEquals("c1", state.consultations[0].consultationId)
        assertEquals("c2", state.consultations[1].consultationId)
        job.cancel()
    }

    @Test
    fun `consultations sorted by createdAt descending`() = runTest {
        val entities = listOf(
            makeConsultationEntity("oldest", createdAt = 1000L),
            makeConsultationEntity("newest", createdAt = 5000L),
            makeConsultationEntity("middle", createdAt = 3000L),
        )
        consultationsFlow.value = entities
        sessionFlow.value = makeSession()

        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("newest", state.consultations[0].consultationId)
        assertEquals("middle", state.consultations[1].consultationId)
        assertEquals("oldest", state.consultations[2].consultationId)
        job.cancel()
    }

    @Test
    fun `null session results in empty consultations and no loading`() = runTest {
        sessionFlow.value = null

        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.consultations.isEmpty())
        job.cancel()
    }

    @Test
    fun `backend sync inserts consultations into Room`() = runTest {
        val rows = listOf(
            ConsultationRow(
                consultationId = "c1",
                patientSessionId = "ps1",
                doctorId = "doc1",
                status = "active",
                serviceType = "general",
                consultationFee = 5000,
                sessionDurationMinutes = 15,
                extensionCount = 0,
                originalDurationMinutes = 15,
                createdAt = "2026-03-14T10:00:00Z",
                updatedAt = "2026-03-14T10:00:00Z",
            ),
        )
        coEvery { consultationService.getConsultationsForDoctor("doc1") } returns
            ApiResult.Success(rows)

        sessionFlow.value = makeSession()

        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        coVerify { consultationDao.insertAll(match { it.size == 1 && it[0].consultationId == "c1" }) }
        job.cancel()
    }

    @Test
    fun `backend sync failure does not affect UI state`() = runTest {
        coEvery { consultationService.getConsultationsForDoctor("doc1") } returns
            ApiResult.Error(code = 500, message = "Internal Server Error")

        val entities = listOf(makeConsultationEntity("c1", createdAt = 1000L))
        consultationsFlow.value = entities
        sessionFlow.value = makeSession()

        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.consultations.size)
        assertEquals("c1", state.consultations[0].consultationId)
        assertNull(state.errorMessage)
        job.cancel()
    }

    @Test
    fun `session change triggers new data load`() = runTest {
        sessionFlow.value = null

        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.consultations.isEmpty())

        val entities = listOf(makeConsultationEntity("c1", createdAt = 2000L))
        consultationsFlow.value = entities
        sessionFlow.value = makeSession()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.consultations.size)
        assertEquals("c1", state.consultations[0].consultationId)
        job.cancel()
    }

    @Test
    fun `empty backend response does not call insertAll`() = runTest {
        coEvery { consultationService.getConsultationsForDoctor("doc1") } returns
            ApiResult.Success(emptyList())

        sessionFlow.value = makeSession()

        val viewModel = createViewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        coVerify(exactly = 0) { consultationDao.insertAll(any()) }
        job.cancel()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun createViewModel(): DoctorConsultationListViewModel =
        DoctorConsultationListViewModel(
            authRepository = authRepository,
            consultationDao = consultationDao,
            consultationService = consultationService,
        )

    private fun makeSession(userId: String = "doc1"): Session = Session(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = Instant.now().plusSeconds(3600),
        user = User(
            id = userId,
            role = UserRole.DOCTOR,
            fullName = "Dr. Test",
            email = "doc@test.com",
            phone = "+255700000001",
        ),
        createdAt = Instant.now(),
    )

    private fun makeConsultationEntity(
        id: String,
        doctorId: String = "doc1",
        createdAt: Long = System.currentTimeMillis(),
    ): ConsultationEntity = ConsultationEntity(
        consultationId = id,
        patientSessionId = "ps-$id",
        doctorId = doctorId,
        status = "active",
        serviceType = "general",
        consultationFee = 5000,
        sessionStartTime = null,
        sessionEndTime = null,
        sessionDurationMinutes = 15,
        requestExpiresAt = createdAt + 600_000,
        scheduledEndAt = null,
        extensionCount = 0,
        gracePeriodEndAt = null,
        originalDurationMinutes = 15,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}

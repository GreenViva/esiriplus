package com.esiri.esiriplus.feature.doctor.viewmodel

import android.app.Application
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.service.AvailabilitySlotRow
import com.esiri.esiriplus.core.network.service.DoctorAvailabilityService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class DoctorAvailabilityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var availabilityService: DoctorAvailabilityService
    private lateinit var authRepository: AuthRepository
    private lateinit var supabaseClientProvider: SupabaseClientProvider
    private lateinit var tokenManager: TokenManager

    private val sessionFlow = MutableStateFlow<Session?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        availabilityService = mockk()
        authRepository = mockk()
        supabaseClientProvider = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)

        // Default stubs
        every { authRepository.currentSession } returns sessionFlow
        every { tokenManager.getAccessTokenSync() } returns "access-token"
        every { tokenManager.getRefreshTokenSync() } returns "refresh-token"
        coEvery { supabaseClientProvider.importAuthToken(any(), any()) } returns Unit
        coEvery { availabilityService.getSlots(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has isLoading true`() = runTest {
        // Do not emit session yet so init stays in loading
        val vm = createViewModel()
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `slots loaded successfully updates state`() = runTest {
        val slots = listOf(
            AvailabilitySlotRow(
                slotId = "s1",
                doctorId = "doc1",
                dayOfWeek = 1,
                startTime = "08:00",
                endTime = "12:00",
                bufferMinutes = 5,
                isActive = true,
            ),
        )
        coEvery { availabilityService.getSlots("doc1") } returns slots
        sessionFlow.value = testSession()

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(1, vm.uiState.value.slots.size)
        assertEquals("s1", vm.uiState.value.slots[0].slotId)
    }

    @Test
    fun `slots sorted by dayOfWeek and startTime`() = runTest {
        val slots = listOf(
            AvailabilitySlotRow(slotId = "s3", doctorId = "doc1", dayOfWeek = 3, startTime = "10:00", endTime = "12:00"),
            AvailabilitySlotRow(slotId = "s1", doctorId = "doc1", dayOfWeek = 1, startTime = "08:00", endTime = "12:00"),
            AvailabilitySlotRow(slotId = "s2", doctorId = "doc1", dayOfWeek = 1, startTime = "14:00", endTime = "17:00"),
        )
        coEvery { availabilityService.getSlots("doc1") } returns slots
        sessionFlow.value = testSession()

        val vm = createViewModel()
        advanceUntilIdle()

        val result = vm.uiState.value.slots
        assertEquals("s1", result[0].slotId)
        assertEquals("s2", result[1].slotId)
        assertEquals("s3", result[2].slotId)
    }

    @Test
    fun `showAddDialog sets flag to true`() = runTest {
        sessionFlow.value = testSession()
        coEvery { availabilityService.getSlots("doc1") } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()
        assertTrue(vm.uiState.value.showAddDialog)
    }

    @Test
    fun `dismissAddDialog sets flag to false`() = runTest {
        sessionFlow.value = testSession()
        coEvery { availabilityService.getSlots("doc1") } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.showAddDialog()
        assertTrue(vm.uiState.value.showAddDialog)

        vm.dismissAddDialog()
        assertFalse(vm.uiState.value.showAddDialog)
    }

    @Test
    fun `updateEditDay changes editDayOfWeek`() = runTest {
        sessionFlow.value = testSession()
        coEvery { availabilityService.getSlots("doc1") } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateEditDay(5)
        assertEquals(5, vm.uiState.value.editDayOfWeek)
    }

    @Test
    fun `updateEditStartTime changes editStartTime`() = runTest {
        sessionFlow.value = testSession()
        coEvery { availabilityService.getSlots("doc1") } returns emptyList()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateEditStartTime("09:30")
        assertEquals("09:30", vm.uiState.value.editStartTime)
    }

    @Test
    fun `saveSlot calls service and reloads`() = runTest {
        sessionFlow.value = testSession()
        coEvery { availabilityService.getSlots("doc1") } returns emptyList()
        coEvery { availabilityService.insertSlot(any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateEditDay(2)
        vm.updateEditStartTime("10:00")
        vm.updateEditEndTime("14:00")
        vm.updateEditBufferMinutes(10)
        vm.saveSlot()
        advanceUntilIdle()

        coVerify { availabilityService.insertSlot(match {
            it.doctorId == "doc1" &&
            it.dayOfWeek == 2 &&
            it.startTime == "10:00" &&
            it.endTime == "14:00" &&
            it.bufferMinutes == 10
        }) }
        // loadSlots called again after save
        coVerify(atLeast = 2) { availabilityService.getSlots("doc1") }
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `saveSlot failure shows error message`() = runTest {
        sessionFlow.value = testSession()
        coEvery { availabilityService.getSlots("doc1") } returns emptyList()
        coEvery { availabilityService.insertSlot(any()) } throws RuntimeException("Network error")
        every { application.getString(any()) } returns "Failed to save slot"

        val vm = createViewModel()
        advanceUntilIdle()

        vm.saveSlot()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSaving)
        assertEquals("Failed to save slot", vm.uiState.value.errorMessage)
    }

    @Test
    fun `deleteSlot calls service and reloads`() = runTest {
        val slots = listOf(
            AvailabilitySlotRow(slotId = "s1", doctorId = "doc1", dayOfWeek = 1, startTime = "08:00", endTime = "12:00"),
        )
        sessionFlow.value = testSession()
        coEvery { availabilityService.getSlots("doc1") } returns slots
        coEvery { availabilityService.deleteSlot("s1") } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteSlot("s1")
        advanceUntilIdle()

        coVerify { availabilityService.deleteSlot("s1") }
        // loadSlots called again after delete
        coVerify(atLeast = 2) { availabilityService.getSlots("doc1") }
    }

    @Test
    fun `deleteSlot failure shows error message`() = runTest {
        sessionFlow.value = testSession()
        coEvery { availabilityService.getSlots("doc1") } returns emptyList()
        coEvery { availabilityService.deleteSlot(any()) } throws RuntimeException("Delete failed")
        every { application.getString(any()) } returns "Failed to delete slot"

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteSlot("s1")
        advanceUntilIdle()

        assertEquals("Failed to delete slot", vm.uiState.value.errorMessage)
    }

    @Test
    fun `dismissMessages clears both error and success`() = runTest {
        sessionFlow.value = testSession()
        coEvery { availabilityService.getSlots("doc1") } returns emptyList()
        coEvery { availabilityService.insertSlot(any()) } throws RuntimeException("fail")
        every { application.getString(any()) } returns "Error occurred"

        val vm = createViewModel()
        advanceUntilIdle()

        // Trigger an error
        vm.saveSlot()
        advanceUntilIdle()
        assertEquals("Error occurred", vm.uiState.value.errorMessage)

        vm.dismissMessages()
        assertNull(vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.successMessage)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun createViewModel(): DoctorAvailabilityViewModel {
        return DoctorAvailabilityViewModel(
            application = application,
            availabilityService = availabilityService,
            authRepository = authRepository,
            supabaseClientProvider = supabaseClientProvider,
            tokenManager = tokenManager,
        )
    }

    private fun testSession(): Session {
        return Session(
            accessToken = "token",
            refreshToken = "refresh",
            expiresAt = Instant.now().plusSeconds(3600),
            user = User(
                id = "doc1",
                fullName = "Dr. Test",
                email = "doc@test.com",
                phone = "+255700000001",
                role = UserRole.DOCTOR,
            ),
            createdAt = Instant.now(),
        )
    }
}

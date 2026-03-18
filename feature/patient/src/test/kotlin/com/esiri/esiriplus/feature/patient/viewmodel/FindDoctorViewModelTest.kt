package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import io.mockk.coEvery
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FindDoctorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var doctorProfileDao: DoctorProfileDao
    private lateinit var edgeFunctionClient: EdgeFunctionClient

    private val doctorsFlow = MutableStateFlow<List<DoctorProfileEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle(mapOf("serviceCategory" to "GP"))
        doctorProfileDao = mockk()
        edgeFunctionClient = mockk()

        // Default stubs
        every { doctorProfileDao.getBySpecialty("gp") } returns doctorsFlow
        coEvery { doctorProfileDao.insertAll(any()) } returns Unit
        coEvery { doctorProfileDao.deleteStaleBySpecialty(any(), any()) } returns Unit
        coEvery {
            edgeFunctionClient.invoke(any(), any(), any(), any())
        } returns ApiResult.Success("""{"doctors":[]}""")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has isLoading true`() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `doctors loaded from Room are displayed`() = runTest {
        val viewModel = createViewModel()

        val doctors = listOf(
            createDoctorEntity(id = "d1", name = "Dr. Alice", specialty = "gp", isAvailable = true),
            createDoctorEntity(id = "d2", name = "Dr. Bob", specialty = "gp", isAvailable = false),
        )
        doctorsFlow.value = doctors
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.doctors.size)
        assertEquals(2, state.filteredDoctors.size)
        assertEquals("Dr. Alice", state.doctors[0].fullName)
        assertEquals("Dr. Bob", state.doctors[1].fullName)
    }

    @Test
    fun `search query filters doctors by name`() = runTest {
        val viewModel = createViewModel()

        doctorsFlow.value = listOf(
            createDoctorEntity(id = "d1", name = "Dr. Alice", specialty = "gp"),
            createDoctorEntity(id = "d2", name = "Dr. Bob", specialty = "gp"),
            createDoctorEntity(id = "d3", name = "Dr. Charlie", specialty = "gp"),
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("bob")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("bob", state.searchQuery)
        assertEquals(1, state.filteredDoctors.size)
        assertEquals("Dr. Bob", state.filteredDoctors[0].fullName)
    }

    @Test
    fun `search query filters doctors by specialty`() = runTest {
        val viewModel = createViewModel()

        doctorsFlow.value = listOf(
            createDoctorEntity(id = "d1", name = "Dr. Alice", specialty = "gp"),
            createDoctorEntity(id = "d2", name = "Dr. Bob", specialty = "specialist"),
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("specialist")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredDoctors.size)
        assertEquals("Dr. Bob", state.filteredDoctors[0].fullName)
    }

    @Test
    fun `availability filter ONLINE shows only available doctors`() = runTest {
        val viewModel = createViewModel()

        doctorsFlow.value = listOf(
            createDoctorEntity(id = "d1", name = "Dr. Alice", isAvailable = true),
            createDoctorEntity(id = "d2", name = "Dr. Bob", isAvailable = false),
            createDoctorEntity(id = "d3", name = "Dr. Charlie", isAvailable = true),
        )
        advanceUntilIdle()

        viewModel.updateAvailabilityFilter(AvailabilityFilter.ONLINE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AvailabilityFilter.ONLINE, state.availabilityFilter)
        assertEquals(2, state.filteredDoctors.size)
        assertTrue(state.filteredDoctors.all { it.isAvailable })
    }

    @Test
    fun `availability filter OFFLINE shows only unavailable doctors`() = runTest {
        val viewModel = createViewModel()

        doctorsFlow.value = listOf(
            createDoctorEntity(id = "d1", name = "Dr. Alice", isAvailable = true),
            createDoctorEntity(id = "d2", name = "Dr. Bob", isAvailable = false),
            createDoctorEntity(id = "d3", name = "Dr. Charlie", isAvailable = true),
        )
        advanceUntilIdle()

        viewModel.updateAvailabilityFilter(AvailabilityFilter.OFFLINE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AvailabilityFilter.OFFLINE, state.availabilityFilter)
        assertEquals(1, state.filteredDoctors.size)
        assertFalse(state.filteredDoctors[0].isAvailable)
        assertEquals("Dr. Bob", state.filteredDoctors[0].fullName)
    }

    @Test
    fun `availability filter ALL shows all doctors`() = runTest {
        val viewModel = createViewModel()

        doctorsFlow.value = listOf(
            createDoctorEntity(id = "d1", name = "Dr. Alice", isAvailable = true),
            createDoctorEntity(id = "d2", name = "Dr. Bob", isAvailable = false),
        )
        advanceUntilIdle()

        // First set a restrictive filter
        viewModel.updateAvailabilityFilter(AvailabilityFilter.ONLINE)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredDoctors.size)

        // Then reset to ALL
        viewModel.updateAvailabilityFilter(AvailabilityFilter.ALL)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AvailabilityFilter.ALL, state.availabilityFilter)
        assertEquals(2, state.filteredDoctors.size)
    }

    @Test
    fun `empty search query shows all doctors`() = runTest {
        val viewModel = createViewModel()

        doctorsFlow.value = listOf(
            createDoctorEntity(id = "d1", name = "Dr. Alice"),
            createDoctorEntity(id = "d2", name = "Dr. Bob"),
        )
        advanceUntilIdle()

        // First filter down
        viewModel.updateSearchQuery("alice")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredDoctors.size)

        // Then clear
        viewModel.updateSearchQuery("")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertEquals(2, state.filteredDoctors.size)
    }

    @Test
    fun `error state set when backend fetch fails`() = runTest {
        val errorMessage = "Failed to load doctors. Please try again."
        every { application.getString(any()) } returns errorMessage

        coEvery {
            edgeFunctionClient.invoke(any(), any(), any(), any())
        } returns ApiResult.Error(code = 500, message = "Internal Server Error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertEquals(errorMessage, state.error)
    }

    @Test
    fun `network error sets error state`() = runTest {
        val errorMessage = "Network error. Please check your connection."
        every { application.getString(any()) } returns errorMessage

        coEvery {
            edgeFunctionClient.invoke(any(), any(), any(), any())
        } returns ApiResult.NetworkError(
            exception = RuntimeException("No internet"),
            message = "No internet",
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertEquals(errorMessage, state.error)
    }

    @Test
    fun `unauthorized error sets error state`() = runTest {
        val errorMessage = "Session expired. Please log in again."
        every { application.getString(any()) } returns errorMessage

        coEvery {
            edgeFunctionClient.invoke(any(), any(), any(), any())
        } returns ApiResult.Unauthorized

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertEquals(errorMessage, state.error)
    }

    @Test
    fun `search and availability filters combine correctly`() = runTest {
        val viewModel = createViewModel()

        doctorsFlow.value = listOf(
            createDoctorEntity(id = "d1", name = "Dr. Alice", isAvailable = true),
            createDoctorEntity(id = "d2", name = "Dr. Alice Smith", isAvailable = false),
            createDoctorEntity(id = "d3", name = "Dr. Bob", isAvailable = true),
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("alice")
        viewModel.updateAvailabilityFilter(AvailabilityFilter.ONLINE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.filteredDoctors.size)
        assertEquals("Dr. Alice", state.filteredDoctors[0].fullName)
        assertTrue(state.filteredDoctors[0].isAvailable)
    }

    @Test
    fun `serviceCategory is mapped to state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("GP", viewModel.uiState.value.serviceCategory)
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun createDoctorEntity(
        id: String = "doctor-1",
        name: String = "Dr. Test",
        specialty: String = "gp",
        isAvailable: Boolean = true,
        bio: String = "Experienced doctor",
        email: String = "doctor@test.com",
        phone: String = "+255700000000",
        languages: List<String> = listOf("English", "Swahili"),
        licenseNumber: String = "LIC-001",
        yearsExperience: Int = 5,
        profilePhotoUrl: String? = null,
        averageRating: Double = 4.5,
        totalRatings: Int = 10,
        isVerified: Boolean = true,
        services: List<String> = listOf("consultation"),
        countryCode: String = "+255",
        country: String = "Tanzania",
    ): DoctorProfileEntity = DoctorProfileEntity(
        doctorId = id,
        fullName = name,
        email = email,
        phone = phone,
        specialty = specialty,
        specialistField = null,
        languages = languages,
        bio = bio,
        licenseNumber = licenseNumber,
        yearsExperience = yearsExperience,
        profilePhotoUrl = profilePhotoUrl,
        averageRating = averageRating,
        totalRatings = totalRatings,
        isVerified = isVerified,
        isAvailable = isAvailable,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L,
        services = services,
        countryCode = countryCode,
        country = country,
    )

    private fun createViewModel(): FindDoctorViewModel = FindDoctorViewModel(
        application = application,
        savedStateHandle = savedStateHandle,
        doctorProfileDao = doctorProfileDao,
        edgeFunctionClient = edgeFunctionClient,
    )
}

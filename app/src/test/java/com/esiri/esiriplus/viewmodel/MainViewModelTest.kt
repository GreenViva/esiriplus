package com.esiri.esiriplus.viewmodel

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.init.DatabaseInitError
import com.esiri.esiriplus.core.database.init.DatabaseInitState
import com.esiri.esiriplus.core.database.init.DatabaseInitializer
import com.esiri.esiriplus.core.domain.model.AuthState
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.core.domain.usecase.LogoutUseCase
import com.esiri.esiriplus.core.domain.usecase.ObserveAuthStateUseCase
import com.esiri.esiriplus.core.domain.usecase.RefreshSessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var observeAuthState: ObserveAuthStateUseCase
    private lateinit var refreshSession: RefreshSessionUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var databaseInitializer: DatabaseInitializer
    private lateinit var authStateFlow: MutableSharedFlow<AuthState>
    private lateinit var dbInitStateFlow: MutableStateFlow<DatabaseInitState>

    private val testUser = User(
        id = "user-1",
        fullName = "Test User",
        phone = "+255700000000",
        role = UserRole.PATIENT,
    )

    private val testSession = Session(
        accessToken = "token",
        refreshToken = "refresh",
        expiresAt = Instant.now().plusSeconds(3600),
        user = testUser,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        observeAuthState = mockk()
        refreshSession = mockk()
        logoutUseCase = mockk(relaxed = true)
        databaseInitializer = mockk()
        authStateFlow = MutableSharedFlow()
        dbInitStateFlow = MutableStateFlow(DatabaseInitState.Idle)
        every { observeAuthState() } returns authStateFlow
        every { databaseInitializer.state } returns dbInitStateFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial appInitState is Loading`() {
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Ready
        val viewModel = createViewModel()
        assertEquals(AppInitState.Loading, viewModel.appInitState.value)
    }

    @Test
    fun `initial authState is Loading`() {
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Ready
        val viewModel = createViewModel()
        assertEquals(AuthState.Loading, viewModel.authState.value)
    }

    @Test
    fun `appInitState becomes Ready when database init succeeds`() = runTest {
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Ready

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(AppInitState.Ready, viewModel.appInitState.value)
    }

    @Test
    fun `appInitState is DatabaseError when database init fails`() = runTest {
        val error = DatabaseInitError.VersionDowngrade
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Failed(error)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.appInitState.value is AppInitState.DatabaseError)
        assertEquals(
            error,
            (viewModel.appInitState.value as AppInitState.DatabaseError).error,
        )
    }

    @Test
    fun `auth observation waits for database ready`() = runTest {
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Ready

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Auth flow should now be collecting
        val authenticated = AuthState.Authenticated(testSession)
        authStateFlow.emit(authenticated)
        advanceUntilIdle()

        assertEquals(authenticated, viewModel.authState.value)
    }

    @Test
    fun `auth is not observed when database init fails`() = runTest {
        coEvery { databaseInitializer.initialize() } returns
            DatabaseInitState.Failed(DatabaseInitError.MigrationFailed(RuntimeException("fail")))

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Auth state should remain Loading since observeAuth was never called
        assertEquals(AuthState.Loading, viewModel.authState.value)
    }

    @Test
    fun `emits Authenticated when observe emits Authenticated`() = runTest {
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Ready

        val viewModel = createViewModel()
        advanceUntilIdle()

        val authenticated = AuthState.Authenticated(testSession)
        authStateFlow.emit(authenticated)
        advanceUntilIdle()

        assertEquals(authenticated, viewModel.authState.value)
    }

    @Test
    fun `emits Unauthenticated when observe emits Unauthenticated`() = runTest {
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Ready

        val viewModel = createViewModel()
        advanceUntilIdle()

        authStateFlow.emit(AuthState.Unauthenticated)
        advanceUntilIdle()

        assertEquals(AuthState.Unauthenticated, viewModel.authState.value)
    }

    @Test
    fun `attempts refresh on SessionExpired and succeeds`() = runTest {
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Ready
        coEvery { refreshSession() } returns Result.Success(testSession)

        val viewModel = createViewModel()
        advanceUntilIdle()

        authStateFlow.emit(AuthState.SessionExpired)
        advanceUntilIdle()

        coVerify { refreshSession() }
        coVerify(exactly = 0) { logoutUseCase() }
    }

    @Test
    fun `logs out when refresh fails on SessionExpired`() = runTest {
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Ready
        coEvery { refreshSession() } returns Result.Error(RuntimeException("Refresh failed"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        authStateFlow.emit(AuthState.SessionExpired)
        advanceUntilIdle()

        coVerify { refreshSession() }
        coVerify { logoutUseCase() }
    }

    @Test
    fun `onLogout calls logoutUseCase`() = runTest {
        coEvery { databaseInitializer.initialize() } returns DatabaseInitState.Ready

        val viewModel = createViewModel()

        viewModel.onLogout()
        advanceUntilIdle()

        coVerify { logoutUseCase() }
    }

    private fun createViewModel() = MainViewModel(
        observeAuthState = observeAuthState,
        refreshSession = refreshSession,
        logoutUseCase = logoutUseCase,
        databaseInitializer = databaseInitializer,
    )
}

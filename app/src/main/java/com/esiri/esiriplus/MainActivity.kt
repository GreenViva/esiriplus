package com.esiri.esiriplus

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.esiri.esiriplus.call.IncomingCallOverlay
import com.esiri.esiriplus.call.IncomingCallStateHolder
import com.esiri.esiriplus.core.common.security.RootDetector
import com.esiri.esiriplus.core.domain.model.AuthState
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.fcm.EsiriplusFirebaseMessagingService
import androidx.appcompat.app.AppCompatDelegate
import com.esiri.esiriplus.ui.AccessibilityFab
import com.esiri.esiriplus.ui.OfflineBanner
import com.esiri.esiriplus.ui.preferences.ThemeMode
import com.esiri.esiriplus.ui.preferences.UserPreferencesManager
import com.esiri.esiriplus.feature.auth.biometric.BiometricAuthManager
import com.esiri.esiriplus.feature.auth.biometric.BiometricLockScreen
import com.esiri.esiriplus.feature.doctor.navigation.DoctorVideoCallRoute
import com.esiri.esiriplus.feature.patient.navigation.PatientVideoCallRoute
import com.esiri.esiriplus.lifecycle.AppLifecycleObserver
import com.esiri.esiriplus.lifecycle.BiometricLockStateHolder
import com.esiri.esiriplus.navigation.EsiriplusNavHost
import com.esiri.esiriplus.ui.DatabaseErrorDialog
import com.esiri.esiriplus.ui.theme.EsiriplusTheme
import com.esiri.esiriplus.viewmodel.AppInitState
import com.esiri.esiriplus.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var appLifecycleObserver: AppLifecycleObserver
    @Inject lateinit var biometricLockStateHolder: BiometricLockStateHolder
    @Inject lateinit var biometricAuthManager: BiometricAuthManager
    @Inject lateinit var incomingCallStateHolder: IncomingCallStateHolder
    @Inject lateinit var userPreferencesManager: UserPreferencesManager

    private data class PendingCallNav(val consultationId: String, val callType: String, val roomId: String = "")
    private val pendingCallNavigation = MutableStateFlow<PendingCallNav?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
        val action = intent.getStringExtra("action")
        if (action == "incoming_request") {
            Log.d("MainActivity", "Incoming request action received via onNewIntent")
        }
    }

    private fun handleCallIntent(intent: Intent?) {
        if (intent?.action == EsiriplusFirebaseMessagingService.ACTION_ACCEPT_CALL) {
            val consultationId = intent.getStringExtra(EsiriplusFirebaseMessagingService.EXTRA_CONSULTATION_ID) ?: return
            val callType = intent.getStringExtra(EsiriplusFirebaseMessagingService.EXTRA_CALL_TYPE) ?: "VIDEO"
            val roomId = intent.getStringExtra(EsiriplusFirebaseMessagingService.EXTRA_ROOM_ID) ?: ""
            Log.d("MainActivity", "Accept call: consultation=$consultationId type=$callType room=$roomId")
            incomingCallStateHolder.dismiss()
            NotificationManagerCompat.from(this).cancel(EsiriplusFirebaseMessagingService.CALL_NOTIFICATION_ID)
            pendingCallNavigation.value = PendingCallNav(consultationId, callType, roomId)
        }
    }

    private fun navigateToVideoCall(
        navController: NavHostController,
        consultationId: String,
        callType: String,
        userRole: UserRole?,
        roomId: String = "",
    ) {
        when (userRole) {
            UserRole.DOCTOR -> navController.navigate(DoctorVideoCallRoute(consultationId, callType, roomId))
            UserRole.PATIENT -> navController.navigate(PatientVideoCallRoute(consultationId, callType, roomId))
            else -> Log.w("MainActivity", "Cannot navigate to video call: unknown role $userRole")
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        biometricLockStateHolder.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Block rooted, tampered, or emulated devices
        if (RootDetector.isDeviceCompromised(this)) {
            AlertDialog.Builder(this)
                .setTitle("Security Alert")
                .setMessage(
                    "eSIRI+ cannot run on this device.\n\n" +
                        "Rooted, modified, or emulated devices are not supported " +
                        "to protect your sensitive medical data.\n\n" +
                        "Please use an unmodified device."
                )
                .setCancelable(false)
                .setPositiveButton("Close App") { _, _ -> finishAffinity() }
                .show()
            return
        }

        splashScreen.setKeepOnScreenCondition {
            viewModel.appInitState.value is AppInitState.Loading
        }

        // Register process lifecycle observer for app resume lock
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        // Handle accept_call intent when app was killed (cold start)
        handleCallIntent(intent)

        enableEdgeToEdge()
        setContent {
            val themeMode by userPreferencesManager.themeMode
                .collectAsStateWithLifecycle()
            val fontScale by userPreferencesManager.fontScale
                .collectAsStateWithLifecycle()
            val highContrast by userPreferencesManager.highContrast
                .collectAsStateWithLifecycle()
            val reduceMotion by userPreferencesManager.reduceMotion
                .collectAsStateWithLifecycle()

            // Sync AppCompatDelegate night mode so system bars and XML resources follow
            LaunchedEffect(themeMode) {
                val nightMode = when (themeMode) {
                    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }

            EsiriplusTheme(
                themeMode = themeMode,
                fontScale = fontScale,
                highContrast = highContrast,
                reduceMotion = reduceMotion,
            ) {
                val appInitState = viewModel.appInitState.collectAsStateWithLifecycle()

                when (val initState = appInitState.value) {
                    is AppInitState.DatabaseError -> {
                        DatabaseErrorDialog(error = initState.error)
                    }
                    else -> {
                        val navController = rememberNavController()
                        val authState = viewModel.authState.collectAsStateWithLifecycle()
                        val isLocked by biometricLockStateHolder.isLocked
                            .collectAsStateWithLifecycle()

                        // FLAG_SECURE: prevent screenshots on doctor screens
                        val isDoctorAuthenticated = authState.value.let {
                            it is AuthState.Authenticated &&
                                it.session.user.role == UserRole.DOCTOR
                        }
                        LaunchedEffect(isDoctorAuthenticated) {
                            if (isDoctorAuthenticated) {
                                window.setFlags(
                                    WindowManager.LayoutParams.FLAG_SECURE,
                                    WindowManager.LayoutParams.FLAG_SECURE,
                                )
                                // After login/registration the doctor just authenticated
                                // (password + optional biometric enrollment). Don't show
                                // the biometric lock screen again immediately.
                                biometricLockStateHolder.unlock()
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                        }

                        val incomingCall by incomingCallStateHolder.incomingCall
                            .collectAsStateWithLifecycle()
                        val pendingNav by pendingCallNavigation
                            .collectAsStateWithLifecycle()

                        // Handle pending call navigation (from notification accept action)
                        // Key on both pendingNav AND authState so it re-fires when auth becomes Authenticated
                        val currentAuth = authState.value
                        LaunchedEffect(pendingNav, currentAuth) {
                            val nav = pendingNav ?: return@LaunchedEffect
                            val role = (currentAuth as? AuthState.Authenticated)?.session?.user?.role
                            if (role != null) {
                                navigateToVideoCall(navController, nav.consultationId, nav.callType, role, nav.roomId)
                                pendingCallNavigation.value = null
                            }
                        }

                        val isOnline by viewModel.isOnline
                            .collectAsStateWithLifecycle()

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding(),
                        ) {
                            OfflineBanner(isOffline = !isOnline)

                            Box(modifier = Modifier.weight(1f)) {
                            EsiriplusNavHost(
                                navController = navController,
                                authState = authState.value,
                                onLogout = viewModel::onLogout,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding(),
                            )

                            // Overlay biometric lock screen for authenticated doctors
                            if (isLocked && currentAuth is AuthState.Authenticated &&
                                currentAuth.session.user.role == UserRole.DOCTOR
                            ) {
                                BiometricLockScreen(
                                    biometricAuthManager = biometricAuthManager,
                                    doctorName = currentAuth.session.user.fullName,
                                    onUnlocked = { biometricLockStateHolder.unlock() },
                                    onSignOut = viewModel::onLogout,
                                )
                            }

                            // Incoming call overlay
                            val call = incomingCall
                            if (call != null) {
                                IncomingCallOverlay(
                                    incomingCall = call,
                                    onAccept = {
                                        incomingCallStateHolder.dismiss()
                                        NotificationManagerCompat.from(this@MainActivity)
                                            .cancel(EsiriplusFirebaseMessagingService.CALL_NOTIFICATION_ID)
                                        val role = (currentAuth as? AuthState.Authenticated)?.session?.user?.role
                                        navigateToVideoCall(navController, call.consultationId, call.callType, role, call.roomId)
                                    },
                                    onDecline = {
                                        incomingCallStateHolder.dismiss()
                                        NotificationManagerCompat.from(this@MainActivity)
                                            .cancel(EsiriplusFirebaseMessagingService.CALL_NOTIFICATION_ID)
                                    },
                                )
                            }

                            // Floating accessibility settings button
                            AccessibilityFab(
                                preferencesManager = userPreferencesManager,
                            )
                        }
                        }
                    }
                }
            }
        }
    }
}

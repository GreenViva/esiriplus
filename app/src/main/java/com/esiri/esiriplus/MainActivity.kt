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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch
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

    private data class PendingRoyalCheckin(val slotDate: String, val slotHour: Int)
    private val pendingRoyalCheckin = MutableStateFlow<PendingRoyalCheckin?>(null)
    @Inject lateinit var edgeFunctionClient: com.esiri.esiriplus.core.network.EdgeFunctionClient

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
        val action = intent.getStringExtra("action")
        if (action == "incoming_request") {
            Log.d("MainActivity", "Incoming request action received via onNewIntent")
        }
    }

    private fun handleCallIntent(intent: Intent?) {
        // Full-screen intent just opens the app — the in-app overlay handles Accept/Decline
        if (intent?.action == com.esiri.esiriplus.service.IncomingCallService.ACTION_SHOW_INCOMING_CALL) {
            Log.d("MainActivity", "Show incoming call overlay (full-screen intent)")
            showOverLockScreen()
            return
        }

        if (intent?.action == EsiriplusFirebaseMessagingService.ACTION_OPEN_ROYAL_CHECKIN) {
            val slotDate = intent.getStringExtra(EsiriplusFirebaseMessagingService.EXTRA_ROYAL_SLOT_DATE).orEmpty()
            val slotHour = intent.getStringExtra(EsiriplusFirebaseMessagingService.EXTRA_ROYAL_SLOT_HOUR)
                ?.toIntOrNull() ?: 0
            Log.d("MainActivity", "Open Royal check-in: date=$slotDate hour=$slotHour")
            // Cancel the per-slot notification immediately — the doctor has
            // already acted, no point keeping it in the tray.
            try {
                NotificationManagerCompat.from(this)
                    .cancel(EsiriplusFirebaseMessagingService.ROYAL_CHECKIN_NOTIFICATION_ID + slotHour)
            } catch (_: Exception) { }
            pendingRoyalCheckin.value = PendingRoyalCheckin(slotDate, slotHour)
            return
        }

        if (intent?.action == EsiriplusFirebaseMessagingService.ACTION_ACCEPT_CALL) {
            val consultationId = intent.getStringExtra(EsiriplusFirebaseMessagingService.EXTRA_CONSULTATION_ID) ?: return
            val callType = intent.getStringExtra(EsiriplusFirebaseMessagingService.EXTRA_CALL_TYPE) ?: "VIDEO"
            val roomId = intent.getStringExtra(EsiriplusFirebaseMessagingService.EXTRA_ROOM_ID) ?: ""
            Log.d("MainActivity", "Accept call: consultation=$consultationId type=$callType room=$roomId")

            // Show over lock screen so the call UI is visible immediately
            showOverLockScreen()

            incomingCallStateHolder.dismiss()
            NotificationManagerCompat.from(this).cancel(EsiriplusFirebaseMessagingService.CALL_NOTIFICATION_ID)
            NotificationManagerCompat.from(this).cancel(com.esiri.esiriplus.service.IncomingCallService.NOTIFICATION_ID)
            com.esiri.esiriplus.service.IncomingCallService.stop(this)
            pendingCallNavigation.value = PendingCallNav(consultationId, callType, roomId)
        }
    }

    /**
     * Allows the activity to display over the lock screen and turns the screen on.
     * Called when accepting an incoming call so the user doesn't have to unlock first.
     */
    private fun showOverLockScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Fire-and-forget call to the acknowledge-royal-checkin edge function so
     * the cron stops retrying that slot for the day. Failures are silent —
     * worst case the doctor gets one more attempt at +5 minutes.
     */
    /**
     * Patient acknowledged the medication reminder call. Stamps
     * patient_joined_at on the event so the server's auto-complete
     * validator credits the nurse only on a real connection.
     */
    private fun notifyPatientJoined(eventId: String) {
        if (eventId.isBlank()) return
        lifecycleScope.launch {
            try {
                val body = kotlinx.serialization.json.buildJsonObject {
                    put("action", kotlinx.serialization.json.JsonPrimitive("patient_joined"))
                    put("event_id", kotlinx.serialization.json.JsonPrimitive(eventId))
                }
                edgeFunctionClient.invoke("medication-reminder-callback", body)
            } catch (e: Exception) {
                Log.w("MainActivity", "patient_joined notify failed", e)
            }
        }
    }

    private fun acknowledgeRoyalCheckin(slotDate: String, slotHour: Int) {
        if (slotDate.isBlank() || slotHour !in listOf(8, 13, 18)) return
        lifecycleScope.launch {
            try {
                val body = kotlinx.serialization.json.buildJsonObject {
                    put("slot_date", kotlinx.serialization.json.JsonPrimitive(slotDate))
                    put("slot_hour", kotlinx.serialization.json.JsonPrimitive(slotHour))
                }
                edgeFunctionClient.invoke("acknowledge-royal-checkin", body)
            } catch (e: Exception) {
                Log.w("MainActivity", "Royal check-in acknowledge failed", e)
            }
        }
    }

    /** Restore normal lock screen behavior after the call ends. */
    private fun clearLockScreenOverride() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun navigateToVideoCall(
        navController: NavHostController,
        consultationId: String,
        callType: String,
        userRole: UserRole?,
        roomId: String = "",
    ) {
        Log.d("MainActivity", "navigateToVideoCall: role=$userRole consultation=$consultationId type=$callType room=$roomId currentRoute=${navController.currentDestination?.route}")
        try {
            when (userRole) {
                UserRole.DOCTOR -> navController.navigate(DoctorVideoCallRoute(consultationId, callType, roomId))
                UserRole.PATIENT -> navController.navigate(PatientVideoCallRoute(consultationId, callType, roomId))
                else -> Log.w("MainActivity", "Cannot navigate to video call: unknown role $userRole")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to navigate to video call", e)
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
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                        }

                        val incomingCall by incomingCallStateHolder.incomingCall
                            .collectAsStateWithLifecycle()
                        val pendingNav by pendingCallNavigation
                            .collectAsStateWithLifecycle()
                        val pendingRoyal by pendingRoyalCheckin
                            .collectAsStateWithLifecycle()

                        // Handle pending call navigation (from notification accept action)
                        // Key on both pendingNav AND authState so it re-fires when auth becomes Authenticated.
                        // Delay slightly to let EsiriplusNavHost finish its auth→graph navigation first,
                        // otherwise PatientVideoCallRoute may be navigated before PatientGraph is mounted.
                        val currentAuth = authState.value
                        LaunchedEffect(pendingNav, currentAuth) {
                            val nav = pendingNav ?: return@LaunchedEffect
                            val role = (currentAuth as? AuthState.Authenticated)?.session?.user?.role
                            Log.d("MainActivity", "PendingNav LaunchedEffect: nav=$nav role=$role authState=${currentAuth::class.simpleName}")
                            if (role != null) {
                                kotlinx.coroutines.delay(600)
                                navigateToVideoCall(navController, nav.consultationId, nav.callType, role, nav.roomId)
                                pendingCallNavigation.value = null
                                // Clear lock screen override after a delay so the call screen
                                // has time to mount; the video call keeps the screen on itself.
                                kotlinx.coroutines.delay(3000)
                                clearLockScreenOverride()
                            }
                        }

                        // Royal check-in tap from FCM notification → fire-and-forget
                        // acknowledge to suppress remaining attempts at this slot,
                        // then navigate to the doctor's Royal Clients list.
                        LaunchedEffect(pendingRoyal, currentAuth) {
                            val royal = pendingRoyal ?: return@LaunchedEffect
                            val role = (currentAuth as? AuthState.Authenticated)?.session?.user?.role
                            if (role == UserRole.DOCTOR) {
                                acknowledgeRoyalCheckin(royal.slotDate, royal.slotHour)
                                kotlinx.coroutines.delay(400)
                                try {
                                    navController.navigate(
                                        com.esiri.esiriplus.feature.doctor.navigation.RoyalClientsRoute,
                                    )
                                } catch (e: Exception) {
                                    Log.w("MainActivity", "Royal check-in nav failed", e)
                                }
                                pendingRoyalCheckin.value = null
                            }
                        }

                        val isOnline by viewModel.isOnline
                            .collectAsStateWithLifecycle()

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
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
                                        NotificationManagerCompat.from(this@MainActivity)
                                            .cancel(com.esiri.esiriplus.service.IncomingCallService.NOTIFICATION_ID)
                                        com.esiri.esiriplus.service.IncomingCallService.stop(this@MainActivity)
                                        // Medication reminder rings route into the nurse's
                                        // Medical Reminder list (which auto-accepts the ring
                                        // server-side). Royal check-in escalation rings route
                                        // into the CO's coverage list. Other rings join the
                                        // VideoSDK call.
                                        when (call.callerRole) {
                                            "medication_reminder_ring" -> navController.navigate(
                                                com.esiri.esiriplus.feature.doctor.navigation.MedicalReminderListRoute(
                                                    autoAcceptEventId = call.consultationId,
                                                ),
                                            )
                                            "royal_checkin_escalation_ring" -> navController.navigate(
                                                com.esiri.esiriplus.feature.doctor.navigation.RoyalCheckinCoverageRoute(
                                                    autoAcceptEscalationId = call.consultationId,
                                                ),
                                            )
                                            else -> {
                                                // Patient accepting a nurse-initiated medication
                                                // reminder call — stamp patient_joined_at on the
                                                // event so the server's auto-complete validator
                                                // can credit the nurse on a real connection.
                                                call.medReminderEventId?.let { eventId ->
                                                    notifyPatientJoined(eventId)
                                                }
                                                val role = (currentAuth as? AuthState.Authenticated)?.session?.user?.role
                                                navigateToVideoCall(navController, call.consultationId, call.callType, role, call.roomId)
                                            }
                                        }
                                    },
                                    onDecline = {
                                        incomingCallStateHolder.dismiss()
                                        NotificationManagerCompat.from(this@MainActivity)
                                            .cancel(EsiriplusFirebaseMessagingService.CALL_NOTIFICATION_ID)
                                        NotificationManagerCompat.from(this@MainActivity)
                                            .cancel(com.esiri.esiriplus.service.IncomingCallService.NOTIFICATION_ID)
                                        com.esiri.esiriplus.service.IncomingCallService.stop(this@MainActivity)
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

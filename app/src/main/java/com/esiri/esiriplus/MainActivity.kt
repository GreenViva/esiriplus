package com.esiri.esiriplus

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.esiri.esiriplus.core.common.locale.LanguagePreferences
import com.esiri.esiriplus.core.domain.model.AuthState
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.feature.auth.biometric.BiometricAuthManager
import com.esiri.esiriplus.feature.auth.biometric.BiometricLockScreen
import com.esiri.esiriplus.lifecycle.AppLifecycleObserver
import com.esiri.esiriplus.lifecycle.BiometricLockStateHolder
import com.esiri.esiriplus.navigation.EsiriplusNavHost
import com.esiri.esiriplus.ui.DatabaseErrorDialog
import com.esiri.esiriplus.ui.theme.EsiriplusTheme
import com.esiri.esiriplus.viewmodel.AppInitState
import com.esiri.esiriplus.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var appLifecycleObserver: AppLifecycleObserver
    @Inject lateinit var biometricLockStateHolder: BiometricLockStateHolder
    @Inject lateinit var biometricAuthManager: BiometricAuthManager

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LanguagePreferences.getLanguageCode(newBase)
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        biometricLockStateHolder.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            viewModel.appInitState.value is AppInitState.Loading
        }

        // Register process lifecycle observer for app resume lock
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        enableEdgeToEdge()
        setContent {
            EsiriplusTheme {
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

                        Box(modifier = Modifier.fillMaxSize()) {
                            EsiriplusNavHost(
                                navController = navController,
                                authState = authState.value,
                                onLogout = viewModel::onLogout,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding(),
                            )

                            // Overlay biometric lock screen for authenticated doctors
                            val currentAuth = authState.value
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
                        }
                    }
                }
            }
        }
    }
}

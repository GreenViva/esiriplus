package com.esiri.esiriplus

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.esiri.esiriplus.core.common.locale.LanguagePreferences
import com.esiri.esiriplus.navigation.EsiriplusNavHost
import com.esiri.esiriplus.ui.DatabaseErrorDialog
import com.esiri.esiriplus.ui.theme.EsiriplusTheme
import com.esiri.esiriplus.viewmodel.AppInitState
import com.esiri.esiriplus.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LanguagePreferences.getLanguageCode(newBase)
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            viewModel.appInitState.value is AppInitState.Loading
        }

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
                        EsiriplusNavHost(
                            navController = navController,
                            authState = authState.value,
                            onLogout = viewModel::onLogout,
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding(),
                        )
                    }
                }
            }
        }
    }
}

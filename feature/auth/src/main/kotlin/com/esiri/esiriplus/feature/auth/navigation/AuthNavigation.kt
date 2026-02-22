package com.esiri.esiriplus.feature.auth.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.esiri.esiriplus.core.common.locale.LanguagePreferences
import com.esiri.esiriplus.feature.auth.screen.DoctorLoginScreen
import com.esiri.esiriplus.feature.auth.screen.DoctorRegistrationScreen
import com.esiri.esiriplus.feature.auth.screen.DoctorTermsScreen
import com.esiri.esiriplus.feature.auth.screen.TermsScreen
import com.esiri.esiriplus.feature.auth.screen.LanguagePickerScreen
import com.esiri.esiriplus.feature.auth.screen.LanguageSelectionScreen
import com.esiri.esiriplus.feature.auth.screen.AccessRecordsScreen
import com.esiri.esiriplus.feature.auth.screen.PatientRecoveryScreen
import com.esiri.esiriplus.feature.auth.screen.PatientSetupScreen
import com.esiri.esiriplus.feature.auth.screen.RoleSelectionScreen
import com.esiri.esiriplus.feature.auth.screen.SecurityQuestionsSetupScreen
import com.esiri.esiriplus.feature.auth.screen.SplashScreen
import com.esiri.esiriplus.feature.auth.viewmodel.PatientSetupViewModel
import kotlinx.serialization.Serializable

@Serializable object AuthGraph
@Serializable object SplashRoute
@Serializable object LanguageSelectionRoute
@Serializable object LanguagePickerRoute
@Serializable object RoleSelectionRoute
@Serializable object TermsRoute
@Serializable object DoctorLoginRoute
@Serializable object PatientSetupRoute
@Serializable object SecurityQuestionsSetupRoute
@Serializable object DoctorTermsRoute
@Serializable object DoctorRegistrationRoute
@Serializable object AccessRecordsRoute
@Serializable object PatientRecoveryRoute

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onPatientAuthenticated: () -> Unit,
    onDoctorAuthenticated: () -> Unit,
) {
    navigation<AuthGraph>(startDestination = SplashRoute) {
        composable<SplashRoute> {
            val context = LocalContext.current
            SplashScreen(
                onContinue = {
                    val destination = if (LanguagePreferences.hasLanguageBeenSelected(context)) {
                        RoleSelectionRoute
                    } else {
                        LanguageSelectionRoute
                    }
                    navController.navigate(destination) {
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<LanguageSelectionRoute> {
            LanguageSelectionScreen(
                onContinue = {
                    navController.navigate(LanguagePickerRoute)
                },
            )
        }
        composable<LanguagePickerRoute> {
            LanguagePickerScreen(
                onContinue = {
                    navController.navigate(RoleSelectionRoute) {
                        popUpTo(LanguageSelectionRoute) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable<RoleSelectionRoute> {
            RoleSelectionScreen(
                onPatientSelected = { navController.navigate(TermsRoute) },
                onDoctorSelected = { navController.navigate(DoctorLoginRoute) },
                onDoctorRegister = { navController.navigate(DoctorTermsRoute) },
                onRecoverPatientId = { navController.navigate(PatientRecoveryRoute) },
                onHaveMyId = { navController.navigate(AccessRecordsRoute) },
            )
        }
        composable<TermsRoute> {
            TermsScreen(
                onAgree = {
                    navController.navigate(PatientSetupRoute) {
                        popUpTo(TermsRoute) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<DoctorTermsRoute> {
            DoctorTermsScreen(
                onAgree = {
                    navController.navigate(DoctorRegistrationRoute) {
                        popUpTo(DoctorTermsRoute) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<DoctorRegistrationRoute> {
            DoctorRegistrationScreen(
                onComplete = onDoctorAuthenticated,
                onNavigateToLogin = {
                    navController.navigate(DoctorLoginRoute) {
                        popUpTo(DoctorRegistrationRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<PatientSetupRoute> { backStackEntry ->
            val viewModel: PatientSetupViewModel = hiltViewModel(backStackEntry)
            val savedStateHandle = backStackEntry.savedStateHandle
            val questionsCompleted = savedStateHandle.get<Boolean>("recoveryQuestionsCompleted") == true
            LaunchedEffect(questionsCompleted) {
                if (questionsCompleted) {
                    viewModel.onRecoveryQuestionsCompleted()
                    savedStateHandle.remove<Boolean>("recoveryQuestionsCompleted")
                }
            }
            PatientSetupScreen(
                onComplete = onPatientAuthenticated,
                onNavigateToRecoveryQuestions = {
                    navController.navigate(SecurityQuestionsSetupRoute)
                },
                viewModel = viewModel,
            )
        }
        composable<SecurityQuestionsSetupRoute> {
            SecurityQuestionsSetupScreen(
                onComplete = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("recoveryQuestionsCompleted", true)
                    navController.popBackStack()
                },
                onSkip = {
                    navController.popBackStack()
                },
            )
        }
        composable<DoctorLoginRoute> {
            DoctorLoginScreen(
                onAuthenticated = onDoctorAuthenticated,
                onBack = { navController.popBackStack() },
            )
        }
        composable<AccessRecordsRoute> {
            AccessRecordsScreen(
                onAccessGranted = onPatientAuthenticated,
                onBack = { navController.popBackStack() },
                onDontHaveId = {
                    navController.navigate(TermsRoute) {
                        popUpTo(AccessRecordsRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<PatientRecoveryRoute> {
            PatientRecoveryScreen(
                onRecovered = onPatientAuthenticated,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

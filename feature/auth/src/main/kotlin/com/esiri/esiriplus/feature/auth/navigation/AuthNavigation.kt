package com.esiri.esiriplus.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.esiri.esiriplus.feature.auth.screen.DoctorLoginScreen
import com.esiri.esiriplus.feature.auth.screen.PatientOnboardingScreen
import com.esiri.esiriplus.feature.auth.screen.RoleSelectionScreen
import kotlinx.serialization.Serializable

@Serializable object AuthGraph
@Serializable object RoleSelectionRoute
@Serializable object PatientOnboardingRoute
@Serializable object DoctorLoginRoute

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onPatientAuthenticated: () -> Unit,
    onDoctorAuthenticated: () -> Unit,
) {
    navigation<AuthGraph>(startDestination = RoleSelectionRoute) {
        composable<RoleSelectionRoute> {
            RoleSelectionScreen(
                onPatientSelected = { navController.navigate(PatientOnboardingRoute) },
                onDoctorSelected = { navController.navigate(DoctorLoginRoute) },
            )
        }
        composable<PatientOnboardingRoute> {
            PatientOnboardingScreen(
                onAuthenticated = onPatientAuthenticated,
                onBack = { navController.popBackStack() },
            )
        }
        composable<DoctorLoginRoute> {
            DoctorLoginScreen(
                onAuthenticated = onDoctorAuthenticated,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

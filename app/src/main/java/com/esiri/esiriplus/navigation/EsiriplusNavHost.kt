package com.esiri.esiriplus.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.esiri.esiriplus.core.domain.model.AuthState
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.feature.admin.navigation.AdminGraph
import com.esiri.esiriplus.feature.admin.navigation.adminGraph
import com.esiri.esiriplus.feature.auth.navigation.AuthGraph
import com.esiri.esiriplus.feature.auth.navigation.PatientSetupRoute
import com.esiri.esiriplus.feature.auth.navigation.RoleSelectionRoute
import com.esiri.esiriplus.feature.auth.navigation.SecurityQuestionsSetupRoute
import com.esiri.esiriplus.feature.auth.navigation.authGraph
import com.esiri.esiriplus.feature.doctor.navigation.DoctorGraph
import com.esiri.esiriplus.feature.doctor.navigation.doctorGraph
import com.esiri.esiriplus.feature.patient.navigation.PatientGraph
import com.esiri.esiriplus.feature.patient.navigation.patientGraph

@Composable
fun EsiriplusNavHost(
    navController: NavHostController,
    authState: AuthState,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compute startDestination only once to prevent NavHost from recreating the graph
    // when authState changes mid-flow (e.g. session saved during patient setup).
    val startDestination: Any = remember {
        when (authState) {
            is AuthState.Authenticated -> when (authState.session.user.role) {
                UserRole.PATIENT -> PatientGraph
                UserRole.DOCTOR -> DoctorGraph
                UserRole.ADMIN -> AdminGraph
            }
            else -> AuthGraph
        }
    }

    // Track whether we've already navigated for the current authenticated state,
    // so we don't re-navigate on every auth state emission (e.g. token refresh).
    val hasNavigatedForAuth = remember {
        mutableStateOf(startDestination != AuthGraph)
    }

    // React to auth state changes — single source of truth for auth navigation.
    // Only force-navigate on logout/session expiry (when user was previously authenticated)
    // or on first authentication. Let the normal SplashRoute → RoleSelection flow handle
    // initial unauthenticated state so the splash "Tap to continue" screen shows.
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.SessionExpired -> {
                hasNavigatedForAuth.value = false
                navController.navigate(RoleSelectionRoute) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthState.Unauthenticated -> {
                // Only force-navigate if user was previously authenticated (i.e., logged out).
                // On fresh app launch, let the auth graph's SplashRoute show first.
                if (hasNavigatedForAuth.value) {
                    hasNavigatedForAuth.value = false
                    navController.navigate(RoleSelectionRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AuthState.Authenticated -> {
                if (!hasNavigatedForAuth.value) {
                    // Check if patient is still on the setup/recovery screen —
                    // don't yank them to the dashboard until they click "Continue".
                    val currentRoute = navController.currentDestination?.route
                    val onPatientSetup = authState.session.user.role == UserRole.PATIENT &&
                        currentRoute != null &&
                        (currentRoute.contains("PatientSetupRoute") ||
                            currentRoute.contains("SecurityQuestionsSetupRoute"))

                    if (!onPatientSetup) {
                        hasNavigatedForAuth.value = true
                        val dest: Any = when (authState.session.user.role) {
                            UserRole.PATIENT -> PatientGraph
                            UserRole.DOCTOR -> DoctorGraph
                            UserRole.ADMIN -> AdminGraph
                        }
                        navController.navigate(dest) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            else -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        authGraph(
            navController = navController,
            onPatientAuthenticated = {
                // Called when patient finishes setup (clicks "Continue" on PatientSetupScreen).
                // The LaunchedEffect skips auto-nav while on setup, so we navigate here.
                hasNavigatedForAuth.value = true
                navController.navigate(PatientGraph) {
                    popUpTo(0) { inclusive = true }
                }
            },
            // Doctor login/registration auto-navigates via LaunchedEffect.
            onDoctorAuthenticated = {},
        )
        patientGraph(navController = navController)
        doctorGraph(navController = navController, onSignOut = onLogout)
        adminGraph(navController = navController)
    }
}

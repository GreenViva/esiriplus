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
import com.esiri.esiriplus.feature.auth.navigation.RoleSelectionRoute
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

    // React to auth state changes — single source of truth for auth navigation
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Unauthenticated, is AuthState.SessionExpired -> {
                hasNavigatedForAuth.value = false
                navController.navigate(RoleSelectionRoute) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthState.Authenticated -> {
                if (!hasNavigatedForAuth.value) {
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
            // Navigation is now handled by LaunchedEffect reacting to authState changes.
            // These callbacks remain as no-ops for the authGraph API contract.
            onPatientAuthenticated = {},
            onDoctorAuthenticated = {},
        )
        patientGraph(navController = navController)
        doctorGraph(navController = navController, onSignOut = onLogout)
        adminGraph(navController = navController)
    }
}

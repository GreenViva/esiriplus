package com.esiri.esiriplus.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.esiri.esiriplus.core.domain.model.AuthState
import com.esiri.esiriplus.core.domain.model.UserRole
import com.esiri.esiriplus.feature.admin.navigation.AdminGraph
import com.esiri.esiriplus.feature.admin.navigation.adminGraph
import com.esiri.esiriplus.feature.auth.navigation.AuthGraph
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

    // React to auth state changes after initial composition
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Unauthenticated, is AuthState.SessionExpired -> {
                navController.navigate(AuthGraph) {
                    popUpTo(0) { inclusive = true }
                }
            }
            else -> { /* navigation handled by in-flow callbacks */ }
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
                navController.navigate(PatientGraph) {
                    popUpTo(AuthGraph) { inclusive = true }
                }
            },
            onDoctorAuthenticated = {
                navController.navigate(DoctorGraph) {
                    popUpTo(AuthGraph) { inclusive = true }
                }
            },
        )
        patientGraph(navController = navController)
        doctorGraph(navController = navController)
        adminGraph(navController = navController)
    }
}

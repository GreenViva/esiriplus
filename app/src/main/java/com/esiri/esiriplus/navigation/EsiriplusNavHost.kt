package com.esiri.esiriplus.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
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
    modifier: Modifier = Modifier,
    startDestination: Any = AuthGraph,
) {
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

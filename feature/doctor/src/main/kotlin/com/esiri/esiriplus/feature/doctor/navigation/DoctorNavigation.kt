package com.esiri.esiriplus.feature.doctor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.esiri.esiriplus.feature.doctor.screen.DoctorConsultationDetailScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorConsultationListScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorDashboardScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorReportScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorVideoCallScreen
import kotlinx.serialization.Serializable

@Serializable object DoctorGraph
@Serializable object DoctorDashboardRoute
@Serializable object DoctorConsultationListRoute
@Serializable data class DoctorConsultationDetailRoute(val consultationId: String)
@Serializable data class DoctorVideoCallRoute(val consultationId: String)
@Serializable data class DoctorReportRoute(val consultationId: String)

fun NavGraphBuilder.doctorGraph(
    navController: NavController,
    onSignOut: () -> Unit = {},
) {
    navigation<DoctorGraph>(startDestination = DoctorDashboardRoute) {
        composable<DoctorDashboardRoute> {
            DoctorDashboardScreen(
                onNavigateToConsultations = { navController.navigate(DoctorConsultationListRoute) },
                onNavigateToConsultation = { consultationId ->
                    navController.navigate(DoctorConsultationDetailRoute(consultationId))
                },
                onSignOut = onSignOut,
            )
        }
        composable<DoctorConsultationListRoute> {
            DoctorConsultationListScreen(
                onConsultationSelected = { id ->
                    navController.navigate(DoctorConsultationDetailRoute(id))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<DoctorConsultationDetailRoute> {
            DoctorConsultationDetailScreen(
                onStartVideoCall = { id ->
                    navController.navigate(DoctorVideoCallRoute(id))
                },
                onWriteReport = { id ->
                    navController.navigate(DoctorReportRoute(id))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<DoctorVideoCallRoute> {
            DoctorVideoCallScreen(
                onCallEnded = { navController.popBackStack() },
            )
        }
        composable<DoctorReportRoute> {
            DoctorReportScreen(
                onReportSubmitted = { navController.popBackStack(DoctorDashboardRoute, false) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

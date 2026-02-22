package com.esiri.esiriplus.feature.patient.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.esiri.esiriplus.feature.patient.screen.ConsultationHistoryScreen
import com.esiri.esiriplus.feature.patient.screen.PatientConsultationScreen
import com.esiri.esiriplus.feature.patient.screen.PatientHomeScreen
import com.esiri.esiriplus.feature.patient.screen.PatientPaymentScreen
import com.esiri.esiriplus.feature.patient.screen.PatientProfileScreen
import com.esiri.esiriplus.feature.patient.screen.PatientVideoCallScreen
import com.esiri.esiriplus.feature.patient.screen.ReportsScreen
import com.esiri.esiriplus.feature.patient.screen.ServiceLocationScreen
import com.esiri.esiriplus.feature.patient.screen.ServicesScreen
import kotlinx.serialization.Serializable

@Serializable object PatientGraph
@Serializable object PatientHomeRoute
@Serializable data class PatientConsultationRoute(val consultationId: String)
@Serializable data class PatientPaymentRoute(val consultationId: String)
@Serializable data class PatientVideoCallRoute(val consultationId: String)
@Serializable object PatientProfileRoute
@Serializable object ServiceLocationRoute
@Serializable object ServicesRoute
@Serializable object ConsultationHistoryRoute
@Serializable object ReportsRoute

fun NavGraphBuilder.patientGraph(navController: NavController) {
    navigation<PatientGraph>(startDestination = PatientHomeRoute) {
        composable<PatientHomeRoute> {
            PatientHomeScreen(
                onStartConsultation = {
                    navController.navigate(ServiceLocationRoute)
                },
                onNavigateToProfile = { navController.navigate(PatientProfileRoute) },
                onNavigateToReports = { navController.navigate(ReportsRoute) },
                onNavigateToConsultationHistory = { navController.navigate(ConsultationHistoryRoute) },
            )
        }
        composable<ServiceLocationRoute> {
            ServiceLocationScreen(
                onSelectInsideTanzania = {
                    navController.navigate(ServicesRoute)
                },
                onSelectOutsideTanzania = {
                    navController.navigate(PatientConsultationRoute(""))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ServicesRoute> {
            ServicesScreen(
                onServiceSelected = { serviceId ->
                    navController.navigate(PatientConsultationRoute(serviceId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PatientConsultationRoute> {
            PatientConsultationScreen(
                onNavigateToPayment = { consultationId ->
                    navController.navigate(PatientPaymentRoute(consultationId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PatientPaymentRoute> {
            PatientPaymentScreen(
                onPaymentComplete = { consultationId ->
                    navController.navigate(PatientVideoCallRoute(consultationId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PatientVideoCallRoute> {
            PatientVideoCallScreen(
                onCallEnded = { navController.popBackStack(PatientHomeRoute, false) },
            )
        }
        composable<PatientProfileRoute> {
            PatientProfileScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<ConsultationHistoryRoute> {
            ConsultationHistoryScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<ReportsRoute> {
            ReportsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

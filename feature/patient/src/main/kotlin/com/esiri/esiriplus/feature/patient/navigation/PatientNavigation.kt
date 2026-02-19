package com.esiri.esiriplus.feature.patient.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.esiri.esiriplus.feature.patient.screen.PatientConsultationScreen
import com.esiri.esiriplus.feature.patient.screen.PatientHomeScreen
import com.esiri.esiriplus.feature.patient.screen.PatientPaymentScreen
import com.esiri.esiriplus.feature.patient.screen.PatientProfileScreen
import com.esiri.esiriplus.feature.patient.screen.PatientVideoCallScreen
import kotlinx.serialization.Serializable

@Serializable object PatientGraph
@Serializable object PatientHomeRoute
@Serializable data class PatientConsultationRoute(val consultationId: String)
@Serializable data class PatientPaymentRoute(val consultationId: String)
@Serializable data class PatientVideoCallRoute(val consultationId: String)
@Serializable object PatientProfileRoute

fun NavGraphBuilder.patientGraph(navController: NavController) {
    navigation<PatientGraph>(startDestination = PatientHomeRoute) {
        composable<PatientHomeRoute> {
            PatientHomeScreen(
                onStartConsultation = { consultationId ->
                    navController.navigate(PatientConsultationRoute(consultationId))
                },
                onNavigateToProfile = { navController.navigate(PatientProfileRoute) },
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
    }
}

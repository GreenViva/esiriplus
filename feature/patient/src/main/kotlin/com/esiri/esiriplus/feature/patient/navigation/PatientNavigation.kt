package com.esiri.esiriplus.feature.patient.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.esiri.esiriplus.feature.patient.screen.BookAppointmentScreen
import com.esiri.esiriplus.feature.patient.screen.ConsultationHistoryScreen
import com.esiri.esiriplus.feature.patient.screen.PatientAppointmentsScreen
import com.esiri.esiriplus.feature.patient.screen.FindDoctorScreen
import com.esiri.esiriplus.feature.patient.screen.PatientConsultationScreen
import com.esiri.esiriplus.feature.patient.screen.PatientHomeScreen
import com.esiri.esiriplus.feature.patient.screen.ExtensionPaymentScreen
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
@Serializable data class FindDoctorRoute(
    val serviceCategory: String,
    val servicePriceAmount: Int,
    val serviceDurationMinutes: Int,
)
@Serializable data class BookAppointmentRoute(
    val doctorId: String,
    val serviceCategory: String,
    val servicePriceAmount: Int,
    val serviceDurationMinutes: Int,
)
@Serializable object PatientAppointmentsRoute
@Serializable data class ExtensionPaymentRoute(
    val consultationId: String,
    val amount: Int,
    val serviceType: String,
)
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
                onResumeConsultation = { consultationId ->
                    navController.navigate(PatientConsultationRoute(consultationId))
                },
            )
        }
        composable<ServiceLocationRoute> {
            ServiceLocationScreen(
                onSelectInsideTanzania = {
                    navController.navigate(ServicesRoute)
                },
                onSelectOutsideTanzania = {
                    // International consultations not yet supported — stay on current screen
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ServicesRoute> {
            ServicesScreen(
                onServiceSelected = { category, price, duration ->
                    navController.navigate(
                        FindDoctorRoute(
                            serviceCategory = category,
                            servicePriceAmount = price,
                            serviceDurationMinutes = duration,
                        ),
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<FindDoctorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FindDoctorRoute>()
            FindDoctorScreen(
                servicePriceAmount = route.servicePriceAmount,
                serviceDurationMinutes = route.serviceDurationMinutes,
                onBookAppointment = { doctorId ->
                    navController.navigate(
                        BookAppointmentRoute(
                            doctorId = doctorId,
                            serviceCategory = route.serviceCategory,
                            servicePriceAmount = route.servicePriceAmount,
                            serviceDurationMinutes = route.serviceDurationMinutes,
                        ),
                    )
                },
                onNavigateToConsultation = { consultationId ->
                    navController.navigate(PatientConsultationRoute(consultationId)) {
                        popUpTo<PatientHomeRoute> { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<BookAppointmentRoute> {
            BookAppointmentScreen(
                onBookingSuccess = {
                    navController.navigate(PatientAppointmentsRoute) {
                        popUpTo<PatientHomeRoute> { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PatientConsultationRoute> {
            PatientConsultationScreen(
                onNavigateToPayment = { consultationId ->
                    navController.navigate(PatientPaymentRoute(consultationId))
                },
                onNavigateToExtensionPayment = { consultationId, amount, serviceType ->
                    navController.navigate(
                        ExtensionPaymentRoute(
                            consultationId = consultationId,
                            amount = amount,
                            serviceType = serviceType,
                        ),
                    )
                },
                onBack = {
                    navController.popBackStack(PatientHomeRoute, inclusive = false)
                },
            )
        }
        composable<ExtensionPaymentRoute> {
            ExtensionPaymentScreen(
                onPaymentComplete = {
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                },
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
        composable<PatientAppointmentsRoute> {
            PatientAppointmentsScreen(
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

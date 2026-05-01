package com.esiri.esiriplus.feature.doctor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.esiri.esiriplus.feature.doctor.screen.DoctorAppointmentsScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorAvailabilitySettingsScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorConsultationDetailScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorConsultationListScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorDashboardScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorNotificationsScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorReportScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorUnsubmittedReportsScreen
import com.esiri.esiriplus.feature.doctor.screen.DoctorVideoCallScreen
import com.esiri.esiriplus.feature.doctor.screen.MedicalReminderListScreen
import com.esiri.esiriplus.feature.doctor.screen.RoyalClientsScreen
import kotlinx.serialization.Serializable

@Serializable object DoctorGraph
@Serializable object DoctorDashboardRoute
@Serializable object DoctorNotificationsRoute
@Serializable object DoctorConsultationListRoute
@Serializable data class DoctorConsultationDetailRoute(val consultationId: String)
@Serializable data class DoctorVideoCallRoute(val consultationId: String, val callType: String = "VIDEO", val roomId: String = "")
@Serializable data class DoctorReportRoute(val consultationId: String)
@Serializable object DoctorAppointmentsRoute
@Serializable object DoctorAvailabilitySettingsRoute
@Serializable object RoyalClientsRoute
@Serializable object DoctorUnsubmittedReportsRoute
/**
 * Nurse Medical Reminder list. [autoAcceptEventId] is non-null when navigated
 * from a ring push — the screen calls accept_ring on first load.
 */
@Serializable data class MedicalReminderListRoute(val autoAcceptEventId: String? = null)

fun NavGraphBuilder.doctorGraph(
    navController: NavController,
    onSignOut: () -> Unit = {},
) {
    navigation<DoctorGraph>(startDestination = DoctorDashboardRoute) {
        composable<DoctorDashboardRoute> {
            DoctorDashboardScreen(
                onNavigateToConsultations = { navController.navigate(DoctorConsultationListRoute) },
                onNavigateToRoyalClients = { navController.navigate(RoyalClientsRoute) },
                onNavigateToConsultation = { consultationId ->
                    navController.navigate(DoctorConsultationDetailRoute(consultationId))
                },
                onNavigateToNotifications = { navController.navigate(DoctorNotificationsRoute) },
                onNavigateToAppointments = { navController.navigate(DoctorAppointmentsRoute) },
                onNavigateToAvailabilitySettings = { navController.navigate(DoctorAvailabilitySettingsRoute) },
                onNavigateToUnsubmittedReports = { navController.navigate(DoctorUnsubmittedReportsRoute) },
                onNavigateToMedicalReminders = { navController.navigate(MedicalReminderListRoute()) },
                onSignOut = onSignOut,
            )
        }
        composable<MedicalReminderListRoute> {
            MedicalReminderListScreen(
                onBack = { navController.popBackStack() },
                onStartCall = { _, roomId, _, consultationId ->
                    // The video-call screen looks up the consultation by ID,
                    // so we must pass the real consultation_id (not the room
                    // id, which had been wrongly reused there before).
                    navController.navigate(
                        DoctorVideoCallRoute(
                            consultationId = consultationId,
                            callType = "AUDIO",
                            roomId = roomId,
                        ),
                    )
                },
            )
        }
        composable<DoctorUnsubmittedReportsRoute> {
            DoctorUnsubmittedReportsScreen(
                onReportSelected = { consultationId ->
                    // Route to the detail screen — DoctorReportRoute is a
                    // deprecated stub. The detail screen auto-opens the report
                    // bottom sheet when the consultation phase is COMPLETED.
                    navController.navigate(DoctorConsultationDetailRoute(consultationId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<DoctorNotificationsRoute> {
            DoctorNotificationsScreen(
                onBack = { navController.popBackStack() },
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
                onStartCall = { id, callType ->
                    navController.navigate(DoctorVideoCallRoute(id, callType))
                },
                onWriteReport = { id ->
                    navController.navigate(DoctorReportRoute(id))
                },
                onBack = { navController.popBackStack() },
                onConsultationCompleted = {
                    navController.popBackStack(DoctorDashboardRoute, inclusive = false)
                },
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
        composable<DoctorAppointmentsRoute> {
            DoctorAppointmentsScreen(
                onNavigateToConsultation = { consultationId ->
                    navController.navigate(DoctorConsultationDetailRoute(consultationId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<DoctorAvailabilitySettingsRoute> {
            DoctorAvailabilitySettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<RoyalClientsRoute> {
            RoyalClientsScreen(
                onBack = { navController.popBackStack() },
                onOpenConsultation = { consultationId ->
                    navController.navigate(DoctorConsultationDetailRoute(consultationId))
                },
                onStartCall = { consultationId, callType ->
                    navController.navigate(DoctorVideoCallRoute(consultationId, callType))
                },
            )
        }
    }
}

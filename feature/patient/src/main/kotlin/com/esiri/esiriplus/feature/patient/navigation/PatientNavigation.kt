package com.esiri.esiriplus.feature.patient.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.esiri.esiriplus.feature.patient.screen.AgentDashboardScreen
import com.esiri.esiriplus.feature.patient.screen.AgentEarningsScreen
import com.esiri.esiriplus.feature.patient.screen.BookAppointmentScreen
import com.esiri.esiriplus.feature.patient.screen.ConsultationHistoryScreen
import com.esiri.esiriplus.feature.patient.screen.FollowUpWaitingScreen
import com.esiri.esiriplus.feature.patient.screen.OngoingConsultationsScreen
import com.esiri.esiriplus.feature.patient.screen.PastChatDetailScreen
import com.esiri.esiriplus.feature.patient.screen.MedicationScheduleScreen
import com.esiri.esiriplus.feature.patient.screen.MissedConsultationsScreen
import com.esiri.esiriplus.feature.patient.screen.PatientAppointmentsScreen
import com.esiri.esiriplus.feature.patient.screen.FindDoctorScreen
import com.esiri.esiriplus.feature.patient.screen.PatientConsultationScreen
import com.esiri.esiriplus.feature.patient.screen.PatientHomeScreen
import com.esiri.esiriplus.feature.patient.screen.PatientLocationGate
import com.esiri.esiriplus.feature.patient.screen.ExtensionPaymentScreen
import com.esiri.esiriplus.feature.patient.screen.PatientPaymentScreen
import com.esiri.esiriplus.feature.patient.screen.PatientProfileScreen
import com.esiri.esiriplus.feature.patient.screen.PatientVideoCallScreen
import com.esiri.esiriplus.feature.patient.screen.ReportDetailScreen
import com.esiri.esiriplus.feature.patient.screen.ReportsScreen
import com.esiri.esiriplus.feature.patient.screen.ServiceLocationScreen
import com.esiri.esiriplus.feature.patient.screen.ServicesScreen
import com.esiri.esiriplus.feature.patient.screen.TierSelectionScreen
import kotlinx.serialization.Serializable

// ── Route definitions ─────────────────────────────────────────────────────────

@Serializable object PatientGraph
@Serializable object PatientHomeRoute
@Serializable object TierSelectionRoute
@Serializable data class ServiceLocationRoute(val tier: String = "ECONOMY")
@Serializable data class ServicesRoute(
    val tier: String = "ECONOMY",
)
@Serializable data class PatientConsultationRoute(val consultationId: String)
@Serializable data class PatientPaymentRoute(
    val consultationId: String,
    val amount: Int = 0,
    val serviceType: String = "",
)
@Serializable data class PatientVideoCallRoute(val consultationId: String, val callType: String = "VIDEO", val roomId: String = "")
@Serializable object PatientProfileRoute
@Serializable data class FindDoctorRoute(
    val serviceCategory: String,
    val servicePriceAmount: Int,
    val serviceDurationMinutes: Int,
    val serviceTier: String = "ECONOMY",
    val appointmentId: String? = null,
    /** Set when navigated from MissedConsultationsScreen so the create-request
     *  call can include the missed source for server-side consumption. */
    val reconnectSourceKind: String? = null,
    val reconnectSourceId: String? = null,
)
@Serializable data class BookAppointmentRoute(
    val doctorId: String,
    val serviceCategory: String,
    val servicePriceAmount: Int,
    val serviceDurationMinutes: Int,
    val serviceTier: String = "ECONOMY",
)
@Serializable object PatientAppointmentsRoute
@Serializable object MedicationScheduleRoute
@Serializable data class ExtensionPaymentRoute(
    val consultationId: String,
    val amount: Int,
    val serviceType: String,
)
@Serializable object ConsultationHistoryRoute
@Serializable data class PastChatDetailRoute(val consultationId: String)
@Serializable object OngoingConsultationsRoute
@Serializable object MissedConsultationsRoute
@Serializable object ReportsRoute
@Serializable data class ReportDetailRoute(val reportId: String)
@Serializable object AgentDashboardRoute
@Serializable object AgentEarningsRoute
@Serializable data class FollowUpWaitingRoute(
    val parentConsultationId: String,
    val doctorId: String,
    val serviceType: String,
    val serviceTier: String,
)
@Serializable data class SubstituteFollowUpRoute(
    val parentConsultationId: String,
    val originalDoctorId: String,
    val serviceType: String,
    /** Alias for serviceType so FindDoctorViewModel can read it from SavedStateHandle */
    val serviceCategory: String = "",
    val serviceTier: String = "ECONOMY",
    val serviceDurationMinutes: Int = 15,
    val isSubstituteFollowUp: Boolean = true,
)

// ── Navigation graph ──────────────────────────────────────────────────────────

fun NavGraphBuilder.patientGraph(
    navController: NavController,
    onSignOut: () -> Unit = {},
) {
    navigation<PatientGraph>(startDestination = PatientHomeRoute) {

        // ── Home ──────────────────────────────────────────────────────────────
        // Wrapped in PatientLocationGate so location permission is mandatory:
        // a returning patient who revoked it in Settings is forced through
        // the prompt before any home content (or downstream flow) renders.
        composable<PatientHomeRoute> {
            // Resolve PatientHomeViewModel here so the gate can call
            // onLocationGranted() without the home screen needing to.
            val homeViewModel = androidx.hilt.navigation.compose.hiltViewModel<
                com.esiri.esiriplus.feature.patient.viewmodel.PatientHomeViewModel,
            >()
            PatientLocationGate(onGranted = homeViewModel::onLocationGranted) {
                PatientHomeScreen(
                    onStartConsultation = {
                        navController.navigate(TierSelectionRoute)
                    },
                    onNavigateToProfile = { navController.navigate(PatientProfileRoute) },
                    onNavigateToReports = { navController.navigate(ReportsRoute) },
                    onNavigateToConsultationHistory = { navController.navigate(ConsultationHistoryRoute) },
                    onNavigateToAppointments = { navController.navigate(PatientAppointmentsRoute) },
                    onNavigateToOngoingConsultations = { navController.navigate(OngoingConsultationsRoute) },
                    onNavigateToMissedConsultations = { navController.navigate(MissedConsultationsRoute) },
                    onResumeConsultation = { consultationId ->
                        navController.navigate(PatientConsultationRoute(consultationId))
                    },
                    viewModel = homeViewModel,
                )
            }
        }

        // ── Tier selection (entry point) ──────────────────────────────────────
        composable<TierSelectionRoute> {
            TierSelectionScreen(
                onSelectRoyal = {
                    navController.navigate(ServiceLocationRoute(tier = "ROYAL"))
                },
                onSelectEconomy = {
                    navController.navigate(ServiceLocationRoute(tier = "ECONOMY"))
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Inside / Outside Tanzania gateway ─────────────────────────────────
        // Inside-Tanzania routes straight to Services — the patient's actual
        // region/district/ward/street comes from the session (GPS-resolved at
        // app start), so no per-flow district picker exists here.
        composable<ServiceLocationRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ServiceLocationRoute>()
            ServiceLocationScreen(
                onSelectInsideTanzania = {
                    navController.navigate(ServicesRoute(tier = route.tier))
                },
                onSelectOutsideTanzania = {
                    // Coming soon — handled by dialog inside the screen
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Services (receives tier, applies PricingEngine) ───────────────────
        composable<ServicesRoute> {
            ServicesScreen(
                onServiceSelected = { category, price, duration, tier ->
                    navController.navigate(
                        FindDoctorRoute(
                            serviceCategory = category,
                            servicePriceAmount = price,
                            serviceDurationMinutes = duration,
                            serviceTier = tier,
                        ),
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Find doctor ───────────────────────────────────────────────────────
        composable<FindDoctorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FindDoctorRoute>()
            FindDoctorScreen(
                servicePriceAmount = route.servicePriceAmount,
                serviceDurationMinutes = route.serviceDurationMinutes,
                serviceTier = route.serviceTier,
                onBack = { navController.popBackStack() },
                onBookAppointment = { doctorId ->
                    navController.navigate(
                        BookAppointmentRoute(
                            doctorId = doctorId,
                            serviceCategory = route.serviceCategory,
                            servicePriceAmount = route.servicePriceAmount,
                            serviceDurationMinutes = route.serviceDurationMinutes,
                            serviceTier = route.serviceTier,
                        ),
                    )
                },
                onNavigateToConsultation = { consultationId ->
                    navController.navigate(PatientConsultationRoute(consultationId)) {
                        popUpTo<PatientHomeRoute> { inclusive = false }
                    }
                },
            )
        }

        // ── Book appointment (tier stored in route for future use) ────────────
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

        // ── Consultation ──────────────────────────────────────────────────────
        composable<PatientConsultationRoute> {
            PatientConsultationScreen(
                onNavigateToPayment = { consultationId, amount, serviceType ->
                    navController.navigate(PatientPaymentRoute(consultationId, amount, serviceType))
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
                onStartCall = { consultationId, callType ->
                    navController.navigate(PatientVideoCallRoute(consultationId, callType))
                },
                onBack = {
                    navController.popBackStack(PatientHomeRoute, inclusive = false)
                },
            )
        }

        composable<ExtensionPaymentRoute> {
            ExtensionPaymentScreen(
                onPaymentComplete = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
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
                onCallEnded = { navController.popBackStack() },
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
                onConsultationAccepted = { consultationId ->
                    navController.navigate(PatientConsultationRoute(consultationId)) {
                        popUpTo<PatientHomeRoute> { inclusive = false }
                    }
                },
                onFindAnotherDoctor = { serviceType, appointmentId ->
                    navController.navigate(
                        FindDoctorRoute(
                            serviceCategory = serviceType,
                            servicePriceAmount = 0,
                            serviceDurationMinutes = 15,
                            appointmentId = appointmentId,
                        ),
                    )
                },
            )
        }

        composable<MedicationScheduleRoute> {
            MedicationScheduleScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ── Missed consultations ──────────────────────────────────────────
        composable<MissedConsultationsRoute> {
            MissedConsultationsScreen(
                onBack = { navController.popBackStack() },
                onReconnect = { item ->
                    // Mirror substitute-follow-up: jump straight to FindDoctor
                    // for the missed entry's specialty, carrying the source
                    // identifiers so the request-create call can consume them.
                    navController.navigate(
                        FindDoctorRoute(
                            serviceCategory = item.serviceType,
                            servicePriceAmount = item.consultationFee,
                            serviceDurationMinutes = 15,
                            serviceTier = item.serviceTier,
                            reconnectSourceKind = item.sourceKind,
                            reconnectSourceId = item.sourceId,
                        ),
                    )
                },
            )
        }

        composable<OngoingConsultationsRoute> {
            OngoingConsultationsScreen(
                onBack = { navController.popBackStack() },
                onOpenConsultation = { consultationId ->
                    navController.navigate(PatientConsultationRoute(consultationId))
                },
                onRequestFollowUp = { parentConsultationId, doctorId, serviceType, serviceTier ->
                    navController.navigate(
                        FollowUpWaitingRoute(
                            parentConsultationId = parentConsultationId,
                            doctorId = doctorId,
                            serviceType = serviceType,
                            serviceTier = serviceTier,
                        ),
                    )
                },
            )
        }

        // ── Follow-up waiting ─────────────────────────────────────────────────
        composable<FollowUpWaitingRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FollowUpWaitingRoute>()
            FollowUpWaitingScreen(
                onAccepted = { consultationId ->
                    navController.navigate(PatientConsultationRoute(consultationId)) {
                        popUpTo<PatientHomeRoute> { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
                onBookAppointment = {
                    navController.navigate(
                        BookAppointmentRoute(
                            doctorId = route.doctorId,
                            serviceCategory = route.serviceType,
                            servicePriceAmount = 0,
                            serviceDurationMinutes = 15,
                        ),
                    )
                },
                onRequestSubstitute = {
                    navController.navigate(
                        SubstituteFollowUpRoute(
                            parentConsultationId = route.parentConsultationId,
                            originalDoctorId = route.doctorId,
                            serviceType = route.serviceType,
                            serviceCategory = route.serviceType,
                        ),
                    )
                },
            )
        }

        // ── Substitute follow-up (find another doctor for a follow-up) ──────
        composable<SubstituteFollowUpRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SubstituteFollowUpRoute>()
            FindDoctorScreen(
                servicePriceAmount = 0,
                serviceDurationMinutes = route.serviceDurationMinutes,
                serviceTier = route.serviceTier,
                onBack = { navController.popBackStack() },
                onBookAppointment = { doctorId ->
                    navController.navigate(
                        BookAppointmentRoute(
                            doctorId = doctorId,
                            serviceCategory = route.serviceType,
                            servicePriceAmount = 0,
                            serviceDurationMinutes = route.serviceDurationMinutes,
                            serviceTier = route.serviceTier,
                        ),
                    )
                },
                onNavigateToConsultation = { consultationId ->
                    navController.navigate(PatientConsultationRoute(consultationId)) {
                        popUpTo<PatientHomeRoute> { inclusive = false }
                    }
                },
            )
        }

        composable<ConsultationHistoryRoute> {
            ConsultationHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { consultationId ->
                    navController.navigate(PastChatDetailRoute(consultationId))
                },
            )
        }

        composable<PastChatDetailRoute> {
            PastChatDetailScreen(onBack = { navController.popBackStack() })
        }

        composable<ReportsRoute> {
            ReportsScreen(
                onBack = { navController.popBackStack() },
                onReportClick = { reportId ->
                    navController.navigate(ReportDetailRoute(reportId))
                },
            )
        }

        composable<ReportDetailRoute> {
            ReportDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ── Agent Dashboard ─────────────────────────────────────────────────
        composable<AgentDashboardRoute> {
            AgentDashboardScreen(
                onStartConsultation = {
                    navController.navigate(TierSelectionRoute)
                },
                // Agent sign-out must go all the way back to role selection
                // (where doctor / patient / agent entry points live) — not to
                // the patient home, since an agent is not a patient.
                onSignedOut = onSignOut,
                onNavigateToEarnings = {
                    navController.navigate(AgentEarningsRoute)
                },
            )
        }

        // ── Agent Earnings ──────────────────────────────────────────────────
        composable<AgentEarningsRoute> {
            AgentEarningsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

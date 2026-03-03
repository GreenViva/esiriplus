package com.esiri.esiriplus.feature.admin.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.esiri.esiriplus.feature.admin.screen.AdminDashboardScreen
import com.esiri.esiriplus.feature.admin.screen.AdminManageUsersScreen
import com.esiri.esiriplus.feature.admin.screen.AuditLogScreen
import com.esiri.esiriplus.feature.admin.screen.DoctorDetailScreen
import com.esiri.esiriplus.feature.admin.screen.DoctorManagementScreen
import com.esiri.esiriplus.feature.admin.screen.RatingsFeedbackScreen
import com.esiri.esiriplus.feature.admin.viewmodel.AdminDoctorViewModel
import kotlinx.serialization.Serializable

@Serializable object AdminGraph
@Serializable object AdminDashboardRoute
@Serializable object AdminManageUsersRoute
@Serializable object AdminDoctorManagementRoute
@Serializable data class AdminDoctorDetailRoute(val doctorId: String)
@Serializable object AdminRatingsFeedbackRoute
@Serializable object AdminAuditLogRoute

fun NavGraphBuilder.adminGraph(navController: NavController) {
    navigation<AdminGraph>(startDestination = AdminDashboardRoute) {
        composable<AdminDashboardRoute> {
            AdminDashboardScreen(
                onManageDoctors = { navController.navigate(AdminDoctorManagementRoute) },
                onRatingsFeedback = { navController.navigate(AdminRatingsFeedbackRoute) },
                onAuditLog = { navController.navigate(AdminAuditLogRoute) },
            )
        }
        composable<AdminManageUsersRoute> {
            AdminManageUsersScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<AdminDoctorManagementRoute> { backStackEntry ->
            val graphEntry = backStackEntry.let {
                navController.getBackStackEntry(AdminGraph)
            }
            val viewModel: AdminDoctorViewModel = hiltViewModel(graphEntry)

            DoctorManagementScreen(
                viewModel = viewModel,
                onDoctorClick = { doctorId ->
                    navController.navigate(AdminDoctorDetailRoute(doctorId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<AdminDoctorDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AdminDoctorDetailRoute>()
            val graphEntry = backStackEntry.let {
                navController.getBackStackEntry(AdminGraph)
            }
            val viewModel: AdminDoctorViewModel = hiltViewModel(graphEntry)

            DoctorDetailScreen(
                doctorId = route.doctorId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<AdminRatingsFeedbackRoute> {
            RatingsFeedbackScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<AdminAuditLogRoute> {
            AuditLogScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

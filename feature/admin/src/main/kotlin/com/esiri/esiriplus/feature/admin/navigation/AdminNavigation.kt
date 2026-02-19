package com.esiri.esiriplus.feature.admin.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.esiri.esiriplus.feature.admin.screen.AdminDashboardScreen
import com.esiri.esiriplus.feature.admin.screen.AdminManageUsersScreen
import kotlinx.serialization.Serializable

@Serializable object AdminGraph
@Serializable object AdminDashboardRoute
@Serializable object AdminManageUsersRoute

fun NavGraphBuilder.adminGraph(navController: NavController) {
    navigation<AdminGraph>(startDestination = AdminDashboardRoute) {
        composable<AdminDashboardRoute> {
            AdminDashboardScreen(
                onManageUsers = { navController.navigate(AdminManageUsersRoute) },
            )
        }
        composable<AdminManageUsersRoute> {
            AdminManageUsersScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

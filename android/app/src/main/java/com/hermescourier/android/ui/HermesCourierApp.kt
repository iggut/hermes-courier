package com.hermescourier.android.ui

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.hermescourier.android.domain.HermesCourierViewModel
import com.hermescourier.android.ui.screens.ApprovalsScreen
import com.hermescourier.android.ui.screens.DashboardScreen
import com.hermescourier.android.ui.screens.SessionsScreen
import com.hermescourier.android.ui.screens.SettingsScreen

enum class HermesCourierRoute(val route: String, val label: String) {
    Dashboard("dashboard", "Dashboard"),
    Sessions("sessions", "Sessions"),
    Approvals("approvals", "Approvals"),
    Settings("settings", "Settings"),
}

@Composable
fun HermesCourierApp(viewModel: HermesCourierViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: HermesCourierRoute.Dashboard.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                HermesCourierRoute.values().forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                            }
                        },
                        icon = { Text(text = destination.label.first().uppercase()) },
                        label = { Text(text = destination.label) },
                    )
                }
            }
        },
    ) { contentPadding ->
        NavHost(navController = navController, startDestination = HermesCourierRoute.Dashboard.route) {
            composable(HermesCourierRoute.Dashboard.route) {
                DashboardScreen(contentPadding = contentPadding, uiState = uiState, onRefresh = viewModel::refresh)
            }
            composable(HermesCourierRoute.Sessions.route) {
                SessionsScreen(contentPadding = contentPadding, sessions = uiState.sessions)
            }
            composable(HermesCourierRoute.Approvals.route) {
                ApprovalsScreen(
                    contentPadding = contentPadding,
                    approvals = uiState.approvals,
                    onApproveApproval = viewModel::approveApproval,
                    onRejectApproval = viewModel::rejectApproval,
                )
            }
            composable(HermesCourierRoute.Settings.route) {
                SettingsScreen(
                    contentPadding = contentPadding,
                    uiState = uiState,
                    onGatewayUrlChange = viewModel::updateGatewayBaseUrl,
                    onCertificatePasswordChange = viewModel::updateCertificatePassword,
                    onImportCertificate = viewModel::importCertificate,
                    onEnrollmentQrScanned = viewModel::applyEnrollmentQr,
                    onSaveSettings = viewModel::saveSettings,
                    onRefresh = viewModel::refresh,
                )
            }
        }
    }
}

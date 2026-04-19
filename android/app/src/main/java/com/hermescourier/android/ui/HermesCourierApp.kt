
package com.hermescourier.android.ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hermescourier.android.domain.HermesCourierViewModel
import com.hermescourier.android.ui.components.HermesBottomBar
import com.hermescourier.android.ui.components.HermesTopBar
import com.hermescourier.android.ui.navigation.AppRoute
import com.hermescourier.android.ui.screens.ApprovalsScreen
import com.hermescourier.android.ui.screens.ChatScreen
import com.hermescourier.android.ui.screens.DashboardScreen
import com.hermescourier.android.ui.screens.SessionsScreen
import com.hermescourier.android.ui.screens.SettingsScreen

@Composable
fun HermesCourierApp(viewModel: HermesCourierViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { HermesTopBar(status = uiState.bootstrapState, detail = uiState.authStatus) },
        bottomBar = { HermesBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Dashboard.route,
            modifier = Modifier,
        ) {
            composable(AppRoute.Dashboard.route) { DashboardScreen(contentPadding = padding, uiState = uiState) }
            composable(AppRoute.Chat.route) { ChatScreen(contentPadding = padding, conversationEvents = uiState.conversationEvents) }
            composable(AppRoute.Approvals.route) { ApprovalsScreen(contentPadding = padding, approvals = uiState.approvals) }
            composable(AppRoute.Sessions.route) { SessionsScreen(contentPadding = padding, sessions = uiState.sessions) }
            composable(AppRoute.Settings.route) { SettingsScreen(contentPadding = padding, uiState = uiState) }
        }
    }
}

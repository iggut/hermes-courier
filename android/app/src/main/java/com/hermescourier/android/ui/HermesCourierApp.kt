
package com.hermescourier.android.ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hermescourier.android.ui.navigation.AppRoute
import com.hermescourier.android.ui.screens.ApprovalsScreen
import com.hermescourier.android.ui.screens.ChatScreen
import com.hermescourier.android.ui.screens.DashboardScreen
import com.hermescourier.android.ui.screens.SessionsScreen
import com.hermescourier.android.ui.screens.SettingsScreen
import com.hermescourier.android.ui.components.HermesBottomBar
import com.hermescourier.android.ui.components.HermesTopBar

@Composable
fun HermesCourierApp() {
    val navController = rememberNavController()

    Scaffold(
        topBar = { HermesTopBar() },
        bottomBar = { HermesBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Dashboard.route,
            modifier = Modifier,
        ) {
            composable(AppRoute.Dashboard.route) { DashboardScreen(contentPadding = padding) }
            composable(AppRoute.Chat.route) { ChatScreen(contentPadding = padding) }
            composable(AppRoute.Approvals.route) { ApprovalsScreen(contentPadding = padding) }
            composable(AppRoute.Sessions.route) { SessionsScreen(contentPadding = padding) }
            composable(AppRoute.Settings.route) { SettingsScreen(contentPadding = padding) }
        }
    }
}

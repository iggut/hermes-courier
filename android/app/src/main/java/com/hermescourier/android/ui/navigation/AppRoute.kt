
package com.hermescourier.android.ui.navigation

sealed class AppRoute(val route: String, val label: String, val icon: String) {
    data object Dashboard : AppRoute("dashboard", "Dashboard", "⌂")
    data object Chat : AppRoute("chat", "Chat", "✎")
    data object Approvals : AppRoute("approvals", "Approvals", "✓")
    data object Sessions : AppRoute("sessions", "Sessions", "◫")
    data object Settings : AppRoute("settings", "Settings", "⚙")
}

val appRoutes = listOf(
    AppRoute.Dashboard,
    AppRoute.Chat,
    AppRoute.Approvals,
    AppRoute.Sessions,
    AppRoute.Settings,
)

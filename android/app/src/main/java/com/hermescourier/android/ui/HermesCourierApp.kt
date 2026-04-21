package com.hermescourier.android.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermescourier.android.ui.components.GatewayStatusBanner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermescourier.android.domain.HermesCourierViewModel
import com.hermescourier.android.ui.screens.ApprovalDetailScreen
import com.hermescourier.android.ui.screens.ApprovalsScreen
import com.hermescourier.android.ui.screens.DashboardScreen
import com.hermescourier.android.ui.screens.ChatScreen
import com.hermescourier.android.ui.screens.SessionDetailScreen
import com.hermescourier.android.ui.screens.SessionsScreen
import com.hermescourier.android.ui.screens.SettingsScreen

enum class HermesCourierRoute(val route: String, val label: String) {
    Dashboard("dashboard", "Dashboard"),
    Chat("chat", "Chat"),
    Sessions("sessions", "Sessions"),
    Approvals("approvals", "Approvals"),
    Settings("settings", "Settings"),
}

private const val SESSION_DETAIL_ROUTE = "session/{sessionId}"
private const val SESSION_DETAIL_ARG = "sessionId"
private const val APPROVAL_DETAIL_ROUTE = "approval/{approvalId}"
private const val APPROVAL_DETAIL_ARG = "approvalId"

internal fun sessionDetailRoute(sessionId: String): String = "session/${Uri.encode(sessionId)}"
internal fun approvalDetailRoute(approvalId: String): String = "approval/${Uri.encode(approvalId)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesCourierApp(viewModel: HermesCourierViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: HermesCourierRoute.Dashboard.route
    val isDetailRoute = currentRoute.startsWith("session/") || currentRoute.startsWith("approval/")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = routeTitle(currentRoute))
                        Text(
                            text = uiState.bootstrapState,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (isDetailRoute) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                    if (!isDetailRoute) {
                        IconButton(onClick = { navController.navigate(HermesCourierRoute.Settings.route) }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Open settings",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!isDetailRoute) {
                NavigationBar {
                    HermesCourierRoute.values().forEach { destination ->
                        val label = when (destination) {
                            HermesCourierRoute.Chat -> navigationLabel(destination.label, uiState.conversationEvents.size)
                            HermesCourierRoute.Sessions -> navigationLabel(destination.label, uiState.sessions.size)
                            HermesCourierRoute.Approvals -> navigationLabel(destination.label, uiState.approvals.size)
                            else -> destination.label
                        }
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
                            icon = {
                                Icon(
                                    imageVector = when (destination) {
                                        HermesCourierRoute.Dashboard -> Icons.Filled.Home
                                        HermesCourierRoute.Chat -> Icons.Filled.Edit
                                        HermesCourierRoute.Sessions -> Icons.AutoMirrored.Filled.List
                                        HermesCourierRoute.Approvals -> Icons.Filled.CheckCircle
                                        HermesCourierRoute.Settings -> Icons.Filled.Settings
                                    },
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(text = label) },
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            GatewayStatusBanner(
                uiState = uiState,
                onRefresh = viewModel::refresh,
                onTestLiveGateway = viewModel::testLiveGateway,
                onRetryQueuedApprovalActions = viewModel::retryQueuedApprovalActions,
            )
            Box(modifier = Modifier.weight(1f)) {
                NavHost(navController = navController, startDestination = HermesCourierRoute.Dashboard.route) {
            composable(HermesCourierRoute.Dashboard.route) {
                DashboardScreen(
                    contentPadding = contentPadding,
                    uiState = uiState,
                    onRefresh = viewModel::refresh,
                    onOpenSessions = { navController.navigate(HermesCourierRoute.Sessions.route) },
                    onOpenApprovals = { navController.navigate(HermesCourierRoute.Approvals.route) },
                    onOpenSettings = { navController.navigate(HermesCourierRoute.Settings.route) },
                )
            }
            composable(HermesCourierRoute.Chat.route) {
                ChatScreen(
                    contentPadding = contentPadding,
                    conversationEvents = uiState.conversationEvents,
                    conversationActionStatus = uiState.conversationActionStatus,
                    conversationActionError = uiState.conversationActionError,
                    conversationActionState = uiState.conversationActionState,
                    onSendConversationMessage = viewModel::sendConversationMessage,
                )
            }
            composable(HermesCourierRoute.Sessions.route) {
                SessionsScreen(
                    contentPadding = contentPadding,
                    sessions = uiState.sessions,
                    bootstrapState = uiState.bootstrapState,
                    streamStatus = uiState.streamStatus,
                    onOpenSessionDetail = { sessionId -> navController.navigate(sessionDetailRoute(sessionId)) },
                    onRefresh = viewModel::refresh,
                )
            }
            composable(
                route = SESSION_DETAIL_ROUTE,
                arguments = listOf(navArgument(SESSION_DETAIL_ARG) { type = NavType.StringType }),
            ) { entry ->
                val sessionId = entry.arguments?.getString(SESSION_DETAIL_ARG).orEmpty()
                val session = uiState.sessions.firstOrNull { it.sessionId == sessionId }
                if (session == null) {
                    SessionsScreen(
                        contentPadding = contentPadding,
                        sessions = uiState.sessions,
                        bootstrapState = uiState.bootstrapState,
                        streamStatus = uiState.streamStatus,
                        onOpenSessionDetail = { id -> navController.navigate(sessionDetailRoute(id)) },
                        onRefresh = viewModel::refresh,
                    )
                } else {
                    SessionDetailScreen(
                        contentPadding = contentPadding,
                        session = session,
                        onRefresh = viewModel::refresh,
                    )
                }
            }
            composable(HermesCourierRoute.Approvals.route) {
                ApprovalsScreen(
                    contentPadding = contentPadding,
                    approvals = uiState.approvals,
                    bootstrapState = uiState.bootstrapState,
                    streamStatus = uiState.streamStatus,
                    queuedApprovalActions = uiState.queuedApprovalActions,
                    onApproveApproval = viewModel::approveApproval,
                    onRejectApproval = viewModel::rejectApproval,
                    onOpenApprovalDetail = { approvalId -> navController.navigate(approvalDetailRoute(approvalId)) },
                    onRefresh = viewModel::refresh,
                )
            }
            composable(
                route = APPROVAL_DETAIL_ROUTE,
                arguments = listOf(navArgument(APPROVAL_DETAIL_ARG) { type = NavType.StringType }),
            ) { entry ->
                val approvalId = entry.arguments?.getString(APPROVAL_DETAIL_ARG).orEmpty()
                val approval = uiState.approvals.firstOrNull { it.approvalId == approvalId }
                if (approval == null) {
                    ApprovalsScreen(
                        contentPadding = contentPadding,
                        approvals = uiState.approvals,
                        bootstrapState = uiState.bootstrapState,
                        streamStatus = uiState.streamStatus,
                        queuedApprovalActions = uiState.queuedApprovalActions,
                        onApproveApproval = viewModel::approveApproval,
                        onRejectApproval = viewModel::rejectApproval,
                        onOpenApprovalDetail = { id -> navController.navigate(approvalDetailRoute(id)) },
                        onRefresh = viewModel::refresh,
                    )
                } else {
                    ApprovalDetailScreen(
                        contentPadding = contentPadding,
                        approval = approval,
                        onApproveApproval = viewModel::approveApproval,
                        onRejectApproval = viewModel::rejectApproval,
                    )
                }
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
                    onTestLiveGateway = viewModel::testLiveGateway,
                    onFlushQueuedActions = viewModel::retryQueuedApprovalActions,
                    onReconnectRealtime = viewModel::reconnectRealtime,
                    onShareEnrollmentQr = viewModel::shareEnrollmentQr,
                    onCopyEnrollmentQrPayload = viewModel::copyEnrollmentQrPayload,
                    onRetryQueuedApprovalAction = viewModel::retryQueuedApprovalAction,
                    onCopyQueuedApprovalActionDetails = viewModel::copyQueuedApprovalActionDetails,
                    onDismissQueuedApprovalAction = viewModel::dismissQueuedApprovalAction,
                    onRestoreQueuedApprovalAction = viewModel::restoreQueuedApprovalAction,
                )
            }
        }
    }
}
}
}

private fun routeTitle(route: String): String = when {
    route == HermesCourierRoute.Dashboard.route -> "Courier Command Center"
    route == HermesCourierRoute.Sessions.route -> "Sessions"
    route == HermesCourierRoute.Approvals.route -> "Approvals"
    route == HermesCourierRoute.Settings.route -> "Settings"
    route.startsWith("session/") -> "Session detail"
    route.startsWith("approval/") -> "Approval detail"
    else -> "Courier Command Center"
}

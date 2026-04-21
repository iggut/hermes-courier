package com.hermescourier.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.ui.CourierEmptyStateKind
import com.hermescourier.android.ui.courierCardElevation
import com.hermescourier.android.ui.courierEmptyStateIllustration
import com.hermescourier.android.ui.courierHeroCardElevation
import com.hermescourier.android.ui.dashboardFreshnessLabel
import com.hermescourier.android.ui.dashboardNextStep

@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    onRefresh: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val completedSessions = uiState.sessions.count { it.status.contains("completed", ignoreCase = true) }
    val attentionSessions = uiState.sessions.count { it.status.contains("error", ignoreCase = true) }
    val pendingApprovals = uiState.approvals.size
    val activeSessions = uiState.sessions.count { it.status.contains("active", ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            elevation = courierHeroCardElevation(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Courier Command Center",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = "Dashboard", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = connectionModeLine(uiState.bootstrapState),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = uiState.bootstrapState, style = MaterialTheme.typography.bodyLarge)
                Text(text = uiState.authStatus, style = MaterialTheme.typography.bodyMedium)
                Text(text = "Gateway mode: ${uiState.gatewayConnectionMode}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = uiState.gatewayConnectionDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = dashboardFreshnessLabel(
                        lastSyncLabel = uiState.dashboard.lastSyncLabel,
                        streamStatus = uiState.streamStatus,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = dashboardNextStep(
                        bootstrapState = uiState.bootstrapState,
                        pendingApprovals = pendingApprovals,
                        activeSessions = activeSessions,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefresh) { Text(text = "Refresh") }
                    Button(onClick = onOpenSessions) { Text(text = "Open sessions") }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Sessions",
                value = uiState.sessions.size.toString(),
                caption = "Visible in the current snapshot",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Approvals",
                value = pendingApprovals.toString(),
                caption = "Waiting for your decision",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Completed",
                value = completedSessions.toString(),
                caption = "Finished conversations",
            )
        }

        Card(elevation = courierCardElevation()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "What to do next", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Use the Sessions tab to browse live conversations, or move to Approvals when the gateway needs a fast decision.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenApprovals) { Text(text = "Open approvals") }
                    OutlinedButton(onClick = onOpenSettings) { Text(text = "Settings") }
                }
            }
        }

        Card(elevation = courierCardElevation()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Live snapshot", style = MaterialTheme.typography.titleMedium)
                if (uiState.sessions.isEmpty() && uiState.approvals.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        courierEmptyStateIllustration(
                            kind = CourierEmptyStateKind.Dashboard,
                            modifier = Modifier.size(84.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "No live items yet",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "That is normal while the gateway is bootstrapping or when the demo dataset is in use.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Tip: refresh from the top app bar, then open Settings to confirm the gateway URL and certificate if nothing shows up.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else {
                    DashboardSnapshotRow(label = "Active sessions", value = activeSessions.toString())
                    DashboardSnapshotRow(label = "Pending approvals", value = pendingApprovals.toString())
                    DashboardSnapshotRow(label = "Attention needed", value = attentionSessions.toString())
                    DashboardSnapshotRow(label = "Auth status", value = uiState.authStatus)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    caption: String,
) {
    Card(elevation = courierCardElevation(), modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(text = caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardSnapshotRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun connectionModeLine(bootstrapState: String): String = when {
    bootstrapState.contains("demo", ignoreCase = true) -> "Demo mode: the app is using the built-in sample data."
    bootstrapState.contains("unavailable", ignoreCase = true) -> "Gateway unavailable. Open Settings to adjust connection details."
    bootstrapState.contains("ready", ignoreCase = true) -> "Gateway ready. Refresh to pull the latest sessions and approvals."
    else -> "Gateway status: $bootstrapState"
}

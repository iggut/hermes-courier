package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.ui.CourierEmptyStateKind
import com.hermescourier.android.ui.components.CompactStatusStrip
import com.hermescourier.android.ui.courierCardElevation
import com.hermescourier.android.ui.courierEmptyStateIllustration
import com.hermescourier.android.ui.dashboardNextStep

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    onRefresh: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenSettings: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
) {
    val completedSessions = uiState.sessions.count { it.status.contains("completed", ignoreCase = true) }
    val attentionSessions = uiState.sessions.count { it.status.contains("error", ignoreCase = true) }
    val pendingApprovals = uiState.approvals.size
    val activeSessions = uiState.sessions.count { it.status.contains("active", ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompactStatusStrip(
            uiState = uiState,
            onReconnect = onReconnectRealtime,
            onRetryQueued = onRetryQueuedApprovalActions,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                value = uiState.sessions.size.toString(),
                label = "Sessions",
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                value = pendingApprovals.toString(),
                label = "Approvals",
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                value = activeSessions.toString(),
                label = "Active",
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                value = completedSessions.toString(),
                label = "Done",
            )
        }

        Card(elevation = courierCardElevation()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Next step", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = dashboardNextStep(
                        bootstrapState = uiState.bootstrapState,
                        pendingApprovals = pendingApprovals,
                        activeSessions = activeSessions,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(onClick = onOpenSessions) { Text(text = "Sessions") }
                    Button(onClick = onOpenApprovals) { Text(text = "Approvals") }
                    OutlinedButton(onClick = onRefresh) { Text(text = "Refresh") }
                    OutlinedButton(onClick = onOpenSettings) { Text(text = "Settings") }
                }
            }
        }

        Card(elevation = courierCardElevation()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Live snapshot", style = MaterialTheme.typography.titleMedium)
                if (uiState.sessions.isEmpty() && uiState.approvals.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        courierEmptyStateIllustration(
                            kind = CourierEmptyStateKind.Dashboard,
                            modifier = Modifier.size(64.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "No live items yet",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Normal while the gateway is bootstrapping or running demo data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    DashboardSnapshotRow(label = "Active sessions", value = activeSessions.toString())
                    DashboardSnapshotRow(label = "Pending approvals", value = pendingApprovals.toString())
                    DashboardSnapshotRow(label = "Attention needed", value = attentionSessions.toString())
                    DashboardSnapshotRow(label = "Auth", value = uiState.authStatus)
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
) {
    Card(elevation = courierCardElevation(), modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesCourierUiState
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = uiState.bootstrapState, style = MaterialTheme.typography.bodyMedium)
                Text(text = uiState.authStatus, style = MaterialTheme.typography.bodySmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onOpenSessions, modifier = Modifier.weight(1f)) {
                        Text(text = "Sessions")
                    }
                    OutlinedButton(onClick = onOpenApprovals, modifier = Modifier.weight(1f)) {
                        Text(text = "Approvals")
                    }
                }
                Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Refresh now")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Live",
                value = uiState.dashboard.activeSessionCount.toString(),
                caption = "Active sessions",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Queue",
                value = uiState.dashboard.pendingApprovalCount.toString(),
                caption = "Pending approvals",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Done",
                value = completedSessions.toString(),
                caption = "Completed sessions",
            )
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Gateway health", style = MaterialTheme.typography.titleMedium)
                Text(text = "Gateway: ${uiState.gatewaySettings.baseUrl}")
                Text(text = "Stream: ${uiState.streamStatus}")
                Text(text = "Stream reconnect: ${uiState.realtimeReconnectCountdown}")
                LinearProgressIndicator(
                    progress = uiState.realtimeReconnectProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = "Enrollment: ${uiState.enrollmentStatus}")
                Text(text = "Approval actions: ${uiState.approvalActionStatus}")
                Text(
                    text = "Attention sessions: $attentionSessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Next step", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = dashboardNextStep(
                        bootstrapState = uiState.bootstrapState,
                        pendingApprovals = uiState.dashboard.pendingApprovalCount,
                        activeSessions = uiState.dashboard.activeSessionCount,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenSessions) {
                        Text(text = "Review sessions")
                    }
                    OutlinedButton(onClick = onOpenApprovals) {
                        Text(text = "Review approvals")
                    }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Live activity", style = MaterialTheme.typography.titleMedium)
                if (uiState.conversationEvents.isEmpty()) {
                    Text(
                        text = "No conversation history yet. Refresh to pull in the latest live updates from the gateway.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    uiState.conversationEvents.takeLast(4).forEach { event ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = "${event.author} · ${event.timestamp}", style = MaterialTheme.typography.labelMedium)
                            Text(text = event.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Device enrollment", style = MaterialTheme.typography.titleMedium)
                Text(text = "Fingerprint: ${uiState.deviceFingerprint}")
                Text(text = uiState.enrollmentStatus, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onOpenSettings) {
                    Text(text = "Open settings")
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(text = caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun connectionModeLine(bootstrapState: String): String = when {
    bootstrapState.contains("demo", ignoreCase = true) ->
        "Mode: sample data (not connected to a live gateway)."
    bootstrapState.contains("unavailable", ignoreCase = true) ->
        "Mode: gateway unavailable."
    bootstrapState.contains("negotiating", ignoreCase = true) ||
        bootstrapState.contains("bootstrapping", ignoreCase = true) ->
        "Mode: negotiating a secure connection."
    else -> "Mode: live command center."
}

package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesCourierUiState

@Composable
fun DashboardScreen(contentPadding: PaddingValues, uiState: HermesCourierUiState, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(text = uiState.bootstrapState, style = MaterialTheme.typography.bodyMedium)
        Text(text = uiState.authStatus, style = MaterialTheme.typography.bodySmall)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                Text(text = "Connection", style = MaterialTheme.typography.titleMedium)
                Text(text = "Gateway: ${uiState.gatewaySettings.baseUrl}")
                Text(text = "Stream: ${uiState.streamStatus}")
                Text(text = "Stream reconnect: ${uiState.realtimeReconnectCountdown}")
                LinearProgressIndicator(
                    progress = uiState.realtimeReconnectProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = "Enrollment: ${uiState.enrollmentStatus}")
                Text(text = "Approval actions: ${uiState.approvalActionStatus}")
                Button(onClick = onRefresh) {
                    Text(text = "Refresh now")
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                Text(text = "Snapshot", style = MaterialTheme.typography.titleMedium)
                Text(text = "Active sessions: ${uiState.dashboard.activeSessionCount}")
                Text(text = "Pending approvals: ${uiState.dashboard.pendingApprovalCount}")
                Text(text = "Last sync: ${uiState.dashboard.lastSyncLabel}")
                Text(text = "State: ${uiState.dashboard.connectionState}")
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                Text(text = "Recent conversation", style = MaterialTheme.typography.titleMedium)
                uiState.conversationEvents.takeLast(5).forEach { event ->
                    Text(text = "${event.author}: ${event.body}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = event.timestamp, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                Text(text = "Device enrollment", style = MaterialTheme.typography.titleMedium)
                Text(text = "Fingerprint: ${uiState.deviceFingerprint}")
                Text(text = uiState.enrollmentStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

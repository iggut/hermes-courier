package com.hermescourier.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.hermescourier.android.ui.dashboardFreshnessLabel

@Composable
fun GatewayStatusBanner(
    uiState: HermesCourierUiState,
    onRefresh: () -> Unit,
    onTestLiveGateway: (() -> Unit)? = null,
    onRetryQueuedApprovalActions: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mode = uiState.gatewayConnectionMode.lowercase()
    val isLive = mode.contains("live")
    val isChecking = mode.contains("checking")
    val isDemoFallback = mode.contains("demo")
    val isUnavailable = mode.contains("unavailable")

    val containerColor = when {
        isLive -> MaterialTheme.colorScheme.primaryContainer
        isChecking -> MaterialTheme.colorScheme.secondaryContainer
        isDemoFallback -> MaterialTheme.colorScheme.tertiaryContainer
        isUnavailable -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isLive) "Live gateway connected" else "Gateway health",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = uiState.gatewayConnectionMode,
                style = MaterialTheme.typography.titleMedium,
            )
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
                text = "Realtime: ${uiState.streamStatus}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Queued approvals: ${uiState.queuedApprovalActions}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isDemoFallback || isUnavailable) {
                Text(
                    text = "Live gateway failures fall back to on-device demo data and queue approval actions locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onRefresh) {
                    Text(text = "Refresh")
                }
                onTestLiveGateway?.let { action ->
                    Button(onClick = action) {
                        Text(text = "Test live gateway")
                    }
                }
                if (uiState.queuedApprovalActions > 0 && onRetryQueuedApprovalActions != null) {
                    OutlinedButton(onClick = onRetryQueuedApprovalActions) {
                        Text(text = "Retry queued")
                    }
                }
            }
        }
    }
}

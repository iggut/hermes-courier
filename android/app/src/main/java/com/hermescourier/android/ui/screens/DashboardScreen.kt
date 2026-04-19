
package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.ui.components.HermesCard
import com.hermescourier.android.ui.components.PrimaryActionButton
import com.hermescourier.android.ui.components.SectionTitle

@Composable
fun DashboardScreen(contentPadding: PaddingValues, uiState: HermesCourierUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionTitle(
                title = "Mission control",
                subtitle = "Monitor your Hermes agent, approve actions, and jump into live conversation.",
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HermesCard(title = "Gateway state", body = uiState.dashboard.connectionState, trailing = uiState.bootstrapState)
                HermesCard(title = "Active session", body = "${uiState.dashboard.activeSessionCount} live session(s)")
                HermesCard(title = "Pending approvals", body = "${uiState.dashboard.pendingApprovalCount} sensitive actions")
                HermesCard(title = "Latest sync", body = uiState.dashboard.lastSyncLabel)
            }
        }
        item {
            HermesCard(
                title = "Auth status",
                body = uiState.authStatus,
                trailing = "Zero-trust mobile bootstrap",
            )
        }
        item {
            PrimaryActionButton(text = "Refresh secure snapshot") { }
        }
        item {
            Text(
                text = "Planned modules: sessions, memory, cron jobs, logs, push alerts, and model controls.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

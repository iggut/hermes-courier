
package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.ui.components.HermesCard
import com.hermescourier.android.ui.components.SectionTitle

@Composable
fun SettingsScreen(contentPadding: PaddingValues, uiState: HermesCourierUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle(title = "Settings", subtitle = "Configure transport, security, and notification preferences.") }
        item { HermesCard(title = "Connection", body = uiState.dashboard.connectionState, trailing = uiState.bootstrapState) }
        item { HermesCard(title = "Security", body = uiState.authStatus, trailing = "Biometrics + device proof") }
        item { HermesCard(title = "Notifications", body = "Approvals, alerts, and completion routing.") }
        item {
            Text(
                text = "This scaffold is ready for real backend integration.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

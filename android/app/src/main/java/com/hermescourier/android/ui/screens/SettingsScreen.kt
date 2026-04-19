
package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.ui.components.HermesCard
import com.hermescourier.android.ui.components.SectionTitle

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle(title = "Settings", subtitle = "Configure transport, security, and notification preferences.") }
        item { HermesCard(title = "Connection", body = "Hermes gateway URL, certificate pinning, and trust status.") }
        item { HermesCard(title = "Security", body = "Biometrics, session expiry, and device-bound secrets.") }
        item { HermesCard(title = "Notifications", body = "Approval prompts, job completion, and alert routing.") }
        item {
            Text(
                text = "This scaffold is ready for real backend integration.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

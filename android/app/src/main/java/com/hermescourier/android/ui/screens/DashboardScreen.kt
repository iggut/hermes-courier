
package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.ui.components.HermesCard
import com.hermescourier.android.ui.components.PrimaryActionButton
import com.hermescourier.android.ui.components.SectionTitle

private data class DashboardStat(val label: String, val value: String)

@Composable
fun DashboardScreen(contentPadding: PaddingValues) {
    val stats = listOf(
        DashboardStat("Active session", "Agent: awake"),
        DashboardStat("Pending approvals", "2 sensitive actions"),
        DashboardStat("Latest sync", "12 seconds ago"),
    )

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
                subtitle = "Monitor your Hermes agent, approve actions, and jump into a live conversation.",
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                stats.forEach { stat ->
                    HermesCard(title = stat.label, body = stat.value)
                }
            }
        }
        item {
            HermesCard(
                title = "Secure gateway",
                body = "Connect with mutual TLS, hardware-backed credentials, and explicit approvals for risky actions.",
                trailing = "Zero-trust mobile access",
            )
        }
        item {
            PrimaryActionButton(text = "Open live chat") { }
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

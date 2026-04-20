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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.ui.sessionCardSummary
import com.hermescourier.android.ui.sessionDetailSubtitle

@Composable
fun SessionDetailScreen(
    contentPadding: PaddingValues,
    session: HermesSessionSummary,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Session details", style = MaterialTheme.typography.headlineMedium)
        Text(text = session.title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = sessionDetailSubtitle(session),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Session overview", style = MaterialTheme.typography.titleMedium)
                Text(text = "Session ID: ${session.sessionId}")
                Text(text = "Status: ${session.status}")
                Text(text = "Last updated: ${session.updatedAt}")
                Text(text = sessionCardSummary(session), style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "What you can do next", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Refresh the list from the top app bar to pull the latest gateway state. Active sessions usually mean the agent is working on live tasks.",
                )
                Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(text = "Refresh now") }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Reading tips", style = MaterialTheme.typography.titleMedium)
                Text(text = "• Active sessions usually mean the agent is working on live tasks.")
                Text(text = "• Completed sessions are useful for auditing recent activity.")
                Text(text = "• Use the sessions tab to compare multiple entries side by side.")
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(text = "Sync again") }
            }
        }
    }
}

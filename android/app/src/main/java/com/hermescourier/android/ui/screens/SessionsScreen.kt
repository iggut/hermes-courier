package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.hermescourier.android.ui.sessionStatusBadge

@Composable
fun SessionsScreen(
    contentPadding: PaddingValues,
    sessions: List<HermesSessionSummary>,
    bootstrapState: String,
    onOpenSessionDetail: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Sessions", style = MaterialTheme.typography.headlineMedium)
                Text(text = "Browse active and historical Hermes runs.")
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Session snapshot", style = MaterialTheme.typography.titleMedium)
                    Text(text = when {
                        sessions.isEmpty() -> "No sessions have synced yet."
                        else -> "${sessions.size} session${if (sessions.size == 1) "" else "s"} are currently visible."
                    })
                    Text(text = bootstrapState, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRefresh) { Text(text = "Refresh") }
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "No sessions yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = when {
                                bootstrapState.contains("Negotiating", ignoreCase = true) ||
                                    bootstrapState.contains("Bootstrapping", ignoreCase = true) ->
                                    "The gateway is still connecting. Once it is ready, sessions will appear here."
                                bootstrapState.contains("Gateway unavailable", ignoreCase = true) ->
                                    "The gateway could not be reached. Open Settings to check the connection and certificate, then refresh."
                                else ->
                                    "After you refresh from the dashboard or settings, active and historical runs will show up here."
                            },
                        )
                        OutlinedButton(onClick = onRefresh) { Text(text = "Try again") }
                    }
                }
            }
        } else {
            items(sessions, key = { it.sessionId }) { session ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = session.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = sessionCardSummary(session), style = MaterialTheme.typography.bodyMedium)
                        Text(text = sessionStatusBadge(session.status), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(text = "Session ID: ${session.sessionId}", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpenSessionDetail(session.sessionId) }) { Text(text = "View details") }
                        }
                    }
                }
            }
        }
    }
}

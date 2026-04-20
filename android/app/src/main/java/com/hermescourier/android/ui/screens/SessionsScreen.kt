package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesSessionSummary

@Composable
fun SessionsScreen(contentPadding: PaddingValues, sessions: List<HermesSessionSummary>, bootstrapState: String) {
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
        if (sessions.isEmpty()) {
            item {
                val message = when {
                    bootstrapState.contains("Negotiating", ignoreCase = true) ||
                        bootstrapState.contains("Bootstrapping", ignoreCase = true) ->
                        "Loading sessions…"
                    bootstrapState.contains("Gateway unavailable", ignoreCase = true) ->
                        "Sessions unavailable. The gateway could not be reached. Check connection in Settings, then refresh."
                    else ->
                        "No sessions yet. After you refresh from the dashboard or settings, new gateway activity appears here."
                }
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(sessions) { session ->
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                        Text(text = session.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = session.status, style = MaterialTheme.typography.bodyMedium)
                        Text(text = session.updatedAt, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

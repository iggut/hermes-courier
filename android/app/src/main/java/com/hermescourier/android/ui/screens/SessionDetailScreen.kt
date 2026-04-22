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
import com.hermescourier.android.domain.model.HermesEndpointVerificationResult
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.ui.courierHeroCardElevation
import com.hermescourier.android.ui.sessionCardSummary
import com.hermescourier.android.ui.sessionDetailSubtitle

@Composable
fun SessionDetailScreen(
    contentPadding: PaddingValues,
    session: HermesSessionSummary,
    onRefresh: () -> Unit,
    onContinueInChat: (sessionId: String) -> Unit,
    onSessionControlAction: (sessionId: String, action: String) -> Unit,
    sessionControlStatus: String,
    endpointVerificationResults: List<HermesEndpointVerificationResult>,
) {
    val support = rememberSessionControlSupport(endpointVerificationResults)
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

        Card(
            elevation = courierHeroCardElevation(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Continue the conversation", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Opens Chat scoped to this session so you pick up where you left off. The composer stays focused on this session until you exit the context.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Button(
                    onClick = { onContinueInChat(session.sessionId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(text = "Continue in chat") }
            }
        }

        Card(elevation = courierHeroCardElevation(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
                Text(text = "Session control", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "These actions hit live gateway session-control endpoints only; unsupported endpoints are reported explicitly.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { onSessionControlAction(session.sessionId, "pause") },
                    enabled = support.canInvoke,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(text = "Pause session") }
                Button(
                    onClick = { onSessionControlAction(session.sessionId, "resume") },
                    enabled = support.canInvoke,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(text = "Resume session") }
                OutlinedButton(
                    onClick = { onSessionControlAction(session.sessionId, "terminate") },
                    enabled = support.canInvoke,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(text = "Terminate session") }
                Text(
                    text = support.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Status: $sessionControlStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private data class SessionControlSupport(val canInvoke: Boolean, val detail: String)

private fun rememberSessionControlSupport(
    results: List<HermesEndpointVerificationResult>,
): SessionControlSupport {
    val controlResults = results.filter { it.endpoint.startsWith("session-control", ignoreCase = true) }
    if (controlResults.isEmpty()) {
        return SessionControlSupport(
            canInvoke = true,
            detail = "Capability unknown until endpoint verification runs; actions stay enabled.",
        )
    }
    val unsupported = controlResults.count { it.status.equals("unsupported", ignoreCase = true) }
    val ok = controlResults.count { it.status.equals("ok", ignoreCase = true) }
    val failed = controlResults.count { it.status.equals("failed", ignoreCase = true) }
    return when {
        ok > 0 -> SessionControlSupport(
            canInvoke = true,
            detail = "Gateway verified at least one session-control endpoint.",
        )
        unsupported == controlResults.size -> SessionControlSupport(
            canInvoke = false,
            detail = "Gateway reports session-control endpoints unsupported for this environment.",
        )
        failed == controlResults.size -> SessionControlSupport(
            canInvoke = true,
            detail = "Session-control checks failed during verification; you can retry from Settings.",
        )
        else -> SessionControlSupport(
            canInvoke = true,
            detail = "Session-control capability is partially available; see Settings verification for endpoint details.",
        )
    }
}

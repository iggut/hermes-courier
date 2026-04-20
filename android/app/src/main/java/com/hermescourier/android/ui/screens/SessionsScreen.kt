package com.hermescourier.android.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.ui.sessionCardSummary
import com.hermescourier.android.ui.sessionStatusBadge

private val SessionFilters = listOf("All", "Live", "Waiting", "Completed", "Needs attention")

@Composable
fun SessionsScreen(
    contentPadding: PaddingValues,
    sessions: List<HermesSessionSummary>,
    bootstrapState: String,
    onOpenSessionDetail: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("All") }
    var selectedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    val filteredSessions = remember(sessions, searchQuery, statusFilter) {
        sessions.filter { session -> sessionMatchesFilter(session, searchQuery, statusFilter) }
    }
    val visibleLiveCount = filteredSessions.count { it.status.contains("active", ignoreCase = true) }
    val visibleAttentionCount = filteredSessions.count { it.status.contains("error", ignoreCase = true) }
    val selectedSession = selectedSessionId?.let { id -> sessions.firstOrNull { it.sessionId == id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Sessions", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Search, filter, and long-press a session to copy its ID or jump into detail faster.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefresh) { Text(text = "Refresh") }
                    Button(onClick = onRefresh) { Text(text = "Sync now") }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = "Search sessions") },
            placeholder = { Text(text = "Title, status, ID, or summary") },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionFilters.forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { statusFilter = filter },
                    label = { Text(text = filter) },
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Visible",
                value = filteredSessions.size.toString(),
                caption = "Matching the current filters",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Live",
                value = visibleLiveCount.toString(),
                caption = "Active sessions",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Attention",
                value = visibleAttentionCount.toString(),
                caption = "Needs review",
            )
        }

        if (filteredSessions.isEmpty()) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "No sessions match your filters", style = MaterialTheme.typography.titleMedium)
                    Text(text = bootstrapState)
                    Text(text = "Try another search term or switch to All to see every active and archived session.")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filteredSessions, key = { it.sessionId }) { session ->
                    SessionListCard(
                        session = session,
                        onOpenSessionDetail = onOpenSessionDetail,
                        onLongPress = { selectedSessionId = session.sessionId },
                    )
                }
            }
        }
    }

    if (selectedSession != null) {
        SessionQuickActionsDialog(
            session = selectedSession,
            onOpenDetails = {
                selectedSessionId = null
                onOpenSessionDetail(selectedSession.sessionId)
            },
            onCopySessionId = {
                clipboardManager.setText(AnnotatedString(selectedSession.sessionId))
                selectedSessionId = null
            },
            onRefresh = {
                selectedSessionId = null
                onRefresh()
            },
            onDismiss = { selectedSessionId = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionListCard(
    session: HermesSessionSummary,
    onOpenSessionDetail: (String) -> Unit,
    onLongPress: () -> Unit,
) {
    val badge = sessionStatusBadge(session.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpenSessionDetail(session.sessionId) },
                onLongClick = onLongPress,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(text = session.title, style = MaterialTheme.typography.titleMedium)
            Text(text = sessionCardSummary(session), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Updated ${session.updatedAt} · ${session.sessionId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionQuickActionsDialog(
    session: HermesSessionSummary,
    onOpenDetails: () -> Unit,
    onCopySessionId: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Session quick actions", style = MaterialTheme.typography.titleLarge)
                Text(text = session.title, style = MaterialTheme.typography.bodyMedium)
                Text(text = session.sessionId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) { Text(text = "Open details") }
                OutlinedButton(onClick = onCopySessionId, modifier = Modifier.fillMaxWidth()) { Text(text = "Copy session ID") }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(text = "Refresh list") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(text = "Dismiss") }
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    caption: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(text = caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun sessionMatchesFilter(
    session: HermesSessionSummary,
    query: String,
    filter: String,
): Boolean {
    val normalizedQuery = query.trim().lowercase()
    val searchable = listOf(
        session.sessionId,
        session.title,
        session.status,
        session.updatedAt,
        sessionCardSummary(session),
        sessionStatusBadge(session.status),
    ).joinToString(" ") { it.lowercase() }

    val matchesQuery = normalizedQuery.isBlank() || searchable.contains(normalizedQuery)
    val matchesFilter = when (filter) {
        "All" -> true
        "Live" -> session.status.contains("active", ignoreCase = true) || sessionStatusBadge(session.status).contains("Live", ignoreCase = true)
        "Waiting" -> session.status.contains("pending", ignoreCase = true) || sessionStatusBadge(session.status).contains("Waiting", ignoreCase = true)
        "Completed" -> session.status.contains("completed", ignoreCase = true) || sessionStatusBadge(session.status).contains("Completed", ignoreCase = true)
        "Needs attention" -> session.status.contains("error", ignoreCase = true) || sessionStatusBadge(session.status).contains("Needs attention", ignoreCase = true)
        else -> true
    }

    return matchesQuery && matchesFilter
}

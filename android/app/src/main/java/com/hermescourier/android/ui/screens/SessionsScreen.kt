package com.hermescourier.android.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.ui.archiveHint
import com.hermescourier.android.ui.courierCardElevation
import com.hermescourier.android.ui.sessionCardSummary
import com.hermescourier.android.ui.sessionDetailSubtitle
import com.hermescourier.android.ui.sessionEmptyStateMessage
import com.hermescourier.android.ui.sessionEmptyStateTitle
import com.hermescourier.android.ui.sessionStatusBadge

private val SessionFilters = listOf("All", "Live", "Waiting", "Completed", "Needs attention", "Archived")

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
    var archivedSessionIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val clipboardManager = LocalClipboardManager.current
    val archivedSet = remember(archivedSessionIds) { archivedSessionIds.toSet() }

    val visibleSessions = remember(sessions, searchQuery, statusFilter, archivedSessionIds) {
        sessions.filter { session ->
            sessionMatchesFilter(
                session = session,
                query = searchQuery,
                filter = statusFilter,
                archivedSet = archivedSet,
            )
        }
    }
    val visibleLiveCount = visibleSessions.count { it.status.contains("active", ignoreCase = true) }
    val visibleAttentionCount = visibleSessions.count { it.status.contains("error", ignoreCase = true) }
    val archivedCount = archivedSessionIds.size
    val selectedSession = selectedSessionId?.let { id -> sessions.firstOrNull { it.sessionId == id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(elevation = courierCardElevation(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Sessions", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Search, filter, archive locally, and long-press a session to copy its ID or jump into detail faster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = archiveHint(archivedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Visible",
                value = visibleSessions.size.toString(),
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
                caption = "Need a closer look",
            )
        }

        if (visibleSessions.isEmpty()) {
            Card(elevation = courierCardElevation()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = sessionEmptyStateTitle(statusFilter, searchQuery),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = sessionEmptyStateMessage(statusFilter, searchQuery),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (searchQuery.isNotBlank()) {
                            OutlinedButton(onClick = { searchQuery = "" }) { Text(text = "Clear search") }
                        }
                        if (statusFilter != "All") {
                            OutlinedButton(onClick = { statusFilter = "All" }) { Text(text = "Reset filters") }
                        }
                        Button(onClick = onRefresh) { Text(text = "Refresh") }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visibleSessions, key = { it.sessionId }) { session ->
                    val isArchived = session.sessionId in archivedSet
                    SessionListCard(
                        session = session,
                        isArchived = isArchived,
                        onOpenSessionDetail = onOpenSessionDetail,
                        onArchive = {
                            archivedSessionIds = (archivedSessionIds + session.sessionId).distinct()
                            if (selectedSessionId == session.sessionId) {
                                selectedSessionId = null
                            }
                        },
                        onRestore = {
                            archivedSessionIds = archivedSessionIds.filterNot { it == session.sessionId }
                        },
                        onLongPress = { selectedSessionId = session.sessionId },
                    )
                }
            }
        }
    }

    if (selectedSession != null) {
        SessionQuickActionsDialog(
            session = selectedSession,
            isArchived = selectedSession.sessionId in archivedSet,
            onOpenDetails = {
                selectedSessionId = null
                onOpenSessionDetail(selectedSession.sessionId)
            },
            onCopySessionId = {
                clipboardManager.setText(AnnotatedString(selectedSession.sessionId))
                selectedSessionId = null
            },
            onArchive = {
                archivedSessionIds = (archivedSessionIds + selectedSession.sessionId).distinct()
                selectedSessionId = null
            },
            onRestore = {
                archivedSessionIds = archivedSessionIds.filterNot { it == selectedSession.sessionId }
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

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    caption: String,
) {
    Card(elevation = courierCardElevation(), modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
    archivedSet: Set<String>,
): Boolean {
    val text = buildString {
        append(session.sessionId)
        append(' ')
        append(session.title)
        append(' ')
        append(session.status)
        append(' ')
        append(session.updatedAt)
        append(' ')
        append(sessionCardSummary(session))
    }.lowercase()
    val queryMatches = query.isBlank() || text.contains(query.trim().lowercase())
    val isArchived = session.sessionId in archivedSet

    val filterMatches = when (filter) {
        "All" -> !isArchived
        "Archived" -> isArchived
        else -> !isArchived && sessionStatusBadge(session.status).equals(filter, ignoreCase = true)
    }
    return queryMatches && filterMatches
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SessionListCard(
    session: HermesSessionSummary,
    isArchived: Boolean,
    onOpenSessionDetail: (String) -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onLongPress: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { target ->
        when (target) {
            SwipeToDismissBoxValue.EndToStart -> {
                if (!isArchived) {
                    onArchive()
                    true
                } else {
                    false
                }
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                if (isArchived) {
                    onRestore()
                    true
                } else {
                    false
                }
            }
            SwipeToDismissBoxValue.Settled -> false
        }
    })

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val swipeProgress = dismissState.progress.coerceIn(0f, 1f)
            val isRestoreAction = isArchived
            val actionLabel = if (isRestoreAction) "Restore" else "Archive"
            val actionIcon = if (isRestoreAction) Icons.Filled.Refresh else Icons.Filled.List
            val actionContainerColor by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> {
                        if (isRestoreAction) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    }
                    SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surfaceVariant
                },
                label = "sessionSwipeBackgroundColor",
            )
            val actionContentColor by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> {
                        if (isRestoreAction) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    }
                    SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "sessionSwipeContentColor",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(actionContainerColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isArchived) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        tint = actionContentColor,
                        modifier = Modifier.graphicsLayer {
                            val scale = 0.94f + (0.18f * swipeProgress)
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.55f + (0.45f * swipeProgress)
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = actionContentColor,
                        )
                        Text(
                            text = if (isRestoreAction) {
                                "Bring it back to the main list"
                            } else {
                                "Move it out of the active queue"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = actionContentColor.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        },
    ) {
        Card(elevation = courierCardElevation(), 
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onOpenSessionDetail(session.sessionId) },
                    onLongClick = onLongPress,
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isArchived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isArchived) "Archived locally" else sessionStatusBadge(session.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = session.title, style = MaterialTheme.typography.titleMedium)
                Text(text = sessionCardSummary(session), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = sessionDetailSubtitle(session) + if (isArchived) " · Archived on this device" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionQuickActionsDialog(
    session: HermesSessionSummary,
    isArchived: Boolean,
    onOpenDetails: () -> Unit,
    onCopySessionId: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = session.title, style = MaterialTheme.typography.titleLarge)
                Text(text = sessionDetailSubtitle(session), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (isArchived) {
                        "This session is archived locally. Restore it to bring it back into the active list."
                    } else {
                        "Quick actions help you jump into detail, copy the session ID, or archive it locally with one tap."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenDetails) { Text(text = "Open details") }
                    OutlinedButton(onClick = onCopySessionId) { Text(text = "Copy session ID") }
                    if (isArchived) {
                        OutlinedButton(onClick = onRestore) { Text(text = "Restore session") }
                    } else {
                        OutlinedButton(onClick = onArchive) { Text(text = "Archive locally") }
                    }
                    OutlinedButton(onClick = onRefresh) { Text(text = "Refresh") }
                }
            }
        }
    }
}

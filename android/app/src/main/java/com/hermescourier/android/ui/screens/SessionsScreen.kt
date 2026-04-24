package com.hermescourier.android.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.ui.CourierEmptyStateKind
import com.hermescourier.android.ui.components.CompactStatusStrip
import com.hermescourier.android.ui.courierCardElevation
import com.hermescourier.android.ui.courierEmptyStateIllustration
import com.hermescourier.android.ui.sessionCardSummary
import com.hermescourier.android.ui.sessionDetailSubtitle
import com.hermescourier.android.ui.sessionEmptyStateMessage
import com.hermescourier.android.ui.sessionEmptyStateTitle
import com.hermescourier.android.ui.sessionStatusBadge

private val SessionFilters = listOf("All", "Live", "Waiting", "Paused", "Completed", "Needs attention", "Archived")

@Composable
fun SessionsScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    sessions: List<HermesSessionSummary>,
    onOpenSessionDetail: (String) -> Unit,
    onOpenSessionInChat: (String) -> Unit,
    onRefresh: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
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
    val visibleLiveCount = visibleSessions.count {
        it.status.contains("active", ignoreCase = true) || it.status.contains("running", ignoreCase = true)
    }
    val visibleAttentionCount = visibleSessions.count { it.status.contains("error", ignoreCase = true) }
    val archivedCount = archivedSessionIds.size
    val selectedSession = selectedSessionId?.let { id -> sessions.firstOrNull { it.sessionId == id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactStatusStrip(
            uiState = uiState,
            onReconnect = onReconnectRealtime,
            onRetryQueued = onRetryQueuedApprovalActions,
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(text = "Search sessions") },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SessionFilters.forEach { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { statusFilter = filter },
                    label = { Text(text = filter) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${visibleSessions.size} visible",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "· $visibleLiveCount live",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (visibleAttentionCount > 0) {
                Text(
                    text = "· $visibleAttentionCount attention",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (archivedCount > 0) {
                Text(
                    text = "· $archivedCount archived",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (visibleSessions.isEmpty()) {
            Card(elevation = courierCardElevation()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        courierEmptyStateIllustration(
                            kind = CourierEmptyStateKind.Sessions,
                            modifier = Modifier.size(64.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = sessionEmptyStateTitle(statusFilter, searchQuery),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = sessionEmptyStateMessage(statusFilter, searchQuery),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleSessions, key = { it.sessionId }) { session ->
                    val isArchived = session.sessionId in archivedSet
                    SessionListCard(
                        session = session,
                        isArchived = isArchived,
                        onOpenSessionInChat = onOpenSessionInChat,
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
            onOpenInChat = {
                selectedSessionId = null
                onOpenSessionInChat(selectedSession.sessionId)
            },
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
    onOpenSessionInChat: (String) -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onLongPress: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { target ->
        when (target) {
            SwipeToDismissBoxValue.EndToStart -> !isArchived
            SwipeToDismissBoxValue.StartToEnd -> isArchived
            SwipeToDismissBoxValue.Settled -> false
        }
    })

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart && !isArchived) {
            onArchive()
        } else if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd && isArchived) {
            onRestore()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isActivelySwiping = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            val swipeProgress = if (isActivelySwiping) {
                FastOutSlowInEasing.transform(dismissState.progress.coerceIn(0f, 1f))
            } else 0f
            val isRestoreAction = isArchived
            val actionLabel = if (isRestoreAction) "Restore" else "Archive"
            val actionIcon = if (isRestoreAction) Icons.Filled.Refresh else Icons.AutoMirrored.Filled.List
            val actionContainerColor = lerp(
                androidx.compose.ui.graphics.Color.Transparent,
                if (isRestoreAction) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                swipeProgress,
            )
            val actionContentColor = lerp(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0f),
                if (isRestoreAction) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                swipeProgress,
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
                            val scale = 0.9f + (0.2f * swipeProgress)
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.45f + (0.55f * swipeProgress)
                        },
                    )
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = actionContentColor,
                    )
                }
            }
        },
    ) {
        Card(
            elevation = courierCardElevation(),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val isActive = dismissState.targetValue != SwipeToDismissBoxValue.Settled
                    val swipeProgress = if (isActive) {
                        FastOutSlowInEasing.transform(dismissState.progress.coerceIn(0f, 1f))
                    } else 0f
                    val scale = 1f - (0.02f * swipeProgress)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - (0.05f * swipeProgress)
                }
                .combinedClickable(
                    onClick = { onOpenSessionInChat(session.sessionId) },
                    onLongClick = onLongPress,
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isArchived) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (isArchived) "Archived" else sessionStatusBadge(session.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = sessionDetailSubtitle(session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Tap to continue in chat · long-press for details",
                    style = MaterialTheme.typography.labelSmall,
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
    onOpenInChat: () -> Unit,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenInChat, modifier = Modifier.fillMaxWidth()) { Text(text = "Continue in chat") }
                    OutlinedButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) { Text(text = "Open details") }
                    OutlinedButton(onClick = onCopySessionId, modifier = Modifier.fillMaxWidth()) { Text(text = "Copy session ID") }
                    if (isArchived) {
                        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text(text = "Restore session") }
                    } else {
                        OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) { Text(text = "Archive locally") }
                    }
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(text = "Refresh") }
                }
            }
        }
    }
}

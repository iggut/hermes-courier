package com.hermescourier.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesConversationActionState
import com.hermescourier.android.domain.model.HermesConversationEvent
import com.hermescourier.android.domain.model.HermesConversationToolCall
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.ui.ChatSendStateIndicator
import com.hermescourier.android.ui.chatActiveSessionHeadline
import com.hermescourier.android.ui.chatActiveSessionSubtitle
import com.hermescourier.android.ui.chatSendStateIndicator
import com.hermescourier.android.ui.chatSendStateLabel
import com.hermescourier.android.ui.chatShouldGroupWithPrevious
import com.hermescourier.android.ui.components.CompactStatusStrip

@Composable
fun ChatScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    conversationEvents: List<HermesConversationEvent>,
    conversationActionStatus: String,
    conversationActionError: String?,
    conversationActionState: HermesConversationActionState,
    activeSession: HermesSessionSummary?,
    onSendConversationMessage: (String) -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
    onExitActiveSession: () -> Unit,
    onOpenActiveSessionDetail: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAutoScrolledToLatest by rememberSaveable(activeSession?.sessionId) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val isSending = conversationActionState == HermesConversationActionState.Sending
    val isDelivered = conversationActionState == HermesConversationActionState.Sent
    val hasError = conversationActionState == HermesConversationActionState.Failed &&
        !conversationActionError.isNullOrBlank()

    val lastUserEventIndex = remember(conversationEvents) {
        conversationEvents.indexOfLast { it.author.equals("You", ignoreCase = true) }
    }

    // Auto-scroll only on: (a) user just added an optimistic message, or (b) viewer was
    // already near the bottom when a new message arrived. This avoids yanking the view
    // away from an operator re-reading older context.
    val isNearBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            val total = layout.totalItemsCount
            lastVisible.index >= total - 2
        }
    }

    LaunchedEffect(conversationEvents.size, activeSession?.sessionId, isSending) {
        if (conversationEvents.isEmpty()) return@LaunchedEffect
        if (!hasAutoScrolledToLatest) {
            listState.scrollToItem(conversationEvents.lastIndex.coerceAtLeast(0))
            hasAutoScrolledToLatest = true
            return@LaunchedEffect
        }
        if (isNearBottom || isSending) {
            listState.animateScrollToItem(conversationEvents.lastIndex.coerceAtLeast(0))
        }
    }

    LaunchedEffect(conversationActionState) {
        when (conversationActionState) {
            HermesConversationActionState.Sent -> {
                if (pendingDraft != null && draft == pendingDraft) {
                    draft = ""
                }
                pendingDraft = null
                focusManager.clearFocus()
            }

            HermesConversationActionState.Failed -> {
                pendingDraft = null
            }

            HermesConversationActionState.Idle,
            HermesConversationActionState.Sending -> Unit
        }
    }

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

        if (uiState.activeSessionId != null) {
            ActiveSessionHeader(
                activeSessionId = uiState.activeSessionId,
                session = activeSession,
                onExitActiveSession = onExitActiveSession,
                onOpenDetails = { uiState.activeSessionId.let(onOpenActiveSessionDetail) },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (conversationEvents.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (uiState.activeSessionId != null) "No messages in this session yet" else "No messages yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Send an instruction to start the feed. Hermes will reply here once the gateway streams events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = conversationEvents,
                        key = { _, event -> event.eventId },
                    ) { index, event ->
                        val previousAuthor = conversationEvents.getOrNull(index - 1)?.author
                        val groupedWithPrev = chatShouldGroupWithPrevious(previousAuthor, event.author)
                        val isUser = event.author.equals("You", ignoreCase = true)
                        val isLatestUser = isUser && index == lastUserEventIndex
                        val indicator = chatSendStateIndicator(
                            isUserMessage = isUser,
                            isLatestUserMessage = isLatestUser,
                            sending = isSending,
                            failed = hasError,
                            delivered = isDelivered,
                        )
                        ChatMessageBubble(
                            event = event,
                            groupedWithPrevious = groupedWithPrev,
                            sendState = indicator,
                        )
                    }
                }
            }
        }

        if (hasError) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Send failed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = conversationActionError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                if (draft.isNotBlank() && !isSending) {
                                    pendingDraft = draft
                                    onSendConversationMessage(draft)
                                }
                            },
                            enabled = draft.isNotBlank() && !isSending,
                        ) { Text("Retry") }
                        TextButton(
                            onClick = { draft = "" },
                            enabled = draft.isNotBlank() && !isSending,
                        ) { Text("Clear") }
                    }
                }
            }
        }

        ChatComposer(
            draft = draft,
            onDraftChange = { draft = it },
            isSending = isSending,
            focusRequester = focusRequester,
            statusLabel = composerStatusLabel(
                state = conversationActionState,
                statusText = conversationActionStatus,
                activeSessionTitle = activeSession?.title?.takeIf { it.isNotBlank() }
                    ?: uiState.activeSessionId,
            ),
            onSend = {
                if (draft.isNotBlank() && !isSending) {
                    pendingDraft = draft
                    onSendConversationMessage(draft)
                }
            },
            onClear = { draft = "" },
        )
    }
}

@Composable
private fun ActiveSessionHeader(
    activeSessionId: String,
    session: HermesSessionSummary?,
    onExitActiveSession: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = "In session · ${chatActiveSessionHeadline(activeSessionId, session)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = chatActiveSessionSubtitle(activeSessionId, session),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onOpenDetails) { Text(text = "Details") }
            IconButton(onClick = onExitActiveSession) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Exit session context",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    event: HermesConversationEvent,
    groupedWithPrevious: Boolean,
    sendState: ChatSendStateIndicator,
) {
    val author = event.author.ifBlank { "Unknown" }
    val body = event.body.ifBlank { "(empty message body)" }
    val timestamp = event.timestamp
    val isUser = author.equals("You", ignoreCase = true)

    val topPadding = if (groupedWithPrevious) 1.dp else 8.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.86f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (!groupedWithPrevious) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = if (isUser) 0.dp else 8.dp,
                        end = if (isUser) 8.dp else 0.dp,
                        bottom = 2.dp,
                    ),
                )
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    },
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 2.dp else 1.dp),
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            if (!isUser && event.reasoning.orEmpty().isNotBlank()) {
                AssistantDetailCard(
                    stateKey = "${event.eventId}:reasoning",
                    label = "Thinking",
                    summary = event.reasoning.orEmpty().lineSequence().firstOrNull()?.trim().orEmpty(),
                    content = event.reasoning.orEmpty(),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!isUser && event.toolCalls.isNotEmpty()) {
                AssistantDetailCard(
                    stateKey = "${event.eventId}:tool-calls",
                    label = if (event.toolCalls.size == 1) "Action" else "Actions",
                    summary = "${event.toolCalls.size} tool call${if (event.toolCalls.size == 1) "" else "s"}",
                    content = event.toolCalls.joinToString("\n\n") { formatToolCall(it) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            val metaText = buildString {
                if (timestamp.isNotBlank()) append(timestamp)
                val indicatorLabel = chatSendStateLabel(sendState)
                if (indicatorLabel.isNotBlank()) {
                    if (isNotBlank()) append(" · ")
                    append(indicatorLabel)
                }
            }
            if (metaText.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(
                        top = 1.dp,
                        start = if (isUser) 0.dp else 8.dp,
                        end = if (isUser) 8.dp else 0.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    sendStateIcon(sendState)?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = when (sendState) {
                                ChatSendStateIndicator.Failed -> MaterialTheme.colorScheme.error
                                ChatSendStateIndicator.Delivered -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantDetailCard(
    stateKey: String,
    label: String,
    summary: String,
    content: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.isNotBlank() && !expanded) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = if (expanded) "Collapse" else "Expand",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse $label" else "Expand $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun formatToolCall(call: HermesConversationToolCall): String = buildString {
    val name = call.name.ifBlank { "tool" }
    append(name)
    if (call.arguments.isNotBlank()) {
        append("\n")
        append(call.arguments)
    }
    if (call.id.isNotBlank()) {
        append("\n")
        append("#")
        append(call.id)
    }
}

private fun sendStateIcon(state: ChatSendStateIndicator): ImageVector? = when (state) {
    ChatSendStateIndicator.Sending -> null
    ChatSendStateIndicator.Delivered -> Icons.Filled.Check
    ChatSendStateIndicator.Failed -> Icons.Filled.Warning
    ChatSendStateIndicator.None -> null
}

@Composable
private fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    isSending: Boolean,
    focusRequester: FocusRequester,
    statusLabel: String,
    onSend: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Message Hermes") },
                    minLines = 1,
                    maxLines = 5,
                    singleLine = false,
                    enabled = !isSending,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() }),
                )
                if (draft.isNotBlank() && !isSending) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear draft",
                        )
                    }
                }
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && !isSending,
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun composerStatusLabel(
    state: HermesConversationActionState,
    statusText: String,
    activeSessionTitle: String?,
): String {
    val base = when (state) {
        HermesConversationActionState.Sending -> "Sending…"
        HermesConversationActionState.Sent -> "Delivered"
        HermesConversationActionState.Failed -> "Failed — see error above"
        HermesConversationActionState.Idle -> statusText.ifBlank { "Ready · mTLS secure transport" }
    }
    if (activeSessionTitle.isNullOrBlank()) return base
    return "$base · session $activeSessionTitle"
}

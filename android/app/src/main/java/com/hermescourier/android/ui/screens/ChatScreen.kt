package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesConversationActionState
import com.hermescourier.android.domain.model.HermesConversationEvent
import com.hermescourier.android.ui.components.HermesCard
import com.hermescourier.android.ui.components.SectionTitle

@Composable
fun ChatScreen(
    contentPadding: PaddingValues,
    conversationEvents: List<HermesConversationEvent>,
    conversationActionStatus: String,
    conversationActionError: String?,
    conversationActionState: HermesConversationActionState,
    onSendConversationMessage: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingDraft by rememberSaveable { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val isSending = conversationActionState == HermesConversationActionState.Sending
    val hasError = conversationActionState == HermesConversationActionState.Failed && !conversationActionError.isNullOrBlank()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(
            title = "Chat / instruct",
            subtitle = "Send a secure instruction to Hermes and follow the live conversation feed below.",
        )

        HermesCard(
            title = "Message status",
            body = conversationActionStatus,
            trailing = when (conversationActionState) {
                HermesConversationActionState.Sending -> "Sending"
                HermesConversationActionState.Sent -> "Delivered"
                HermesConversationActionState.Failed -> "Needs attention"
                HermesConversationActionState.Idle -> "Ready"
            },
        )

        if (isSending) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (hasError) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Send failed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = conversationActionError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "Review the draft, adjust it if needed, then retry. Your last instruction stays in the composer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (draft.isNotBlank() && !isSending) {
                                    pendingDraft = draft
                                    onSendConversationMessage(draft)
                                }
                            },
                            enabled = draft.isNotBlank() && !isSending,
                        ) {
                            Text("Retry send")
                        }
                        OutlinedButton(
                            onClick = { draft = "" },
                            enabled = draft.isNotBlank() && !isSending,
                        ) {
                            Text("Clear draft")
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            if (conversationEvents.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Send an instruction to start the feed. Hermes will reply here once the gateway streams events.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Try: ‘Summarize the latest session’, ‘Review pending approvals’, or ‘Draft a concise update for the team.’",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(conversationEvents, key = { it.eventId }) { event ->
                        val author = event.author.ifBlank { "Unknown" }
                        val body = event.body.ifBlank { "(empty message body)" }
                        val timestamp = event.timestamp.ifBlank { "unknown time" }
                        val isUser = author.equals("You", ignoreCase = true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.92f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = author,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isUser) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                        Text(
                                            text = timestamp,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isUser) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                    Text(
                                        text = body,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Compose an instruction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Ask Hermes to do something, summarize a session, or review approvals") },
                    minLines = 3,
                    maxLines = 5,
                    singleLine = false,
                    enabled = !isSending,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                        if (draft.isNotBlank() && !isSending) {
                            pendingDraft = draft
                            onSendConversationMessage(draft)
                        }
                    }),
                )
                Text(
                    text = "Secure mTLS transport, realtime feed, and live approvals are all surfaced here.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pendingDraft != null && isSending) {
                    Text(
                        text = "Sending your current instruction now…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (draft.isNotBlank() && !isSending) {
                                pendingDraft = draft
                                onSendConversationMessage(draft)
                            }
                        },
                        enabled = draft.isNotBlank() && !isSending,
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(imageVector = Icons.Filled.Send, contentDescription = "Send instruction")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSending) "Sending" else "Send")
                    }
                    OutlinedButton(
                        onClick = { draft = "" },
                        enabled = draft.isNotBlank() && !isSending,
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

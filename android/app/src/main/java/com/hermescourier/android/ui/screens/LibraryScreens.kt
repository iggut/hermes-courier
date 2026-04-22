package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesCapabilityListing
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.domain.model.HermesCronJob
import com.hermescourier.android.domain.model.HermesLogEntry
import com.hermescourier.android.domain.model.HermesMemoryItem
import com.hermescourier.android.domain.model.HermesSkill
import com.hermescourier.android.ui.components.CompactStatusStrip
import com.hermescourier.android.ui.courierCardElevation

/**
 * Generic scaffold for a read-only capability listing. Renders the compact
 * status strip at the top, an unsupported state when the gateway declared the
 * feature unavailable, and delegates row/detail rendering to the caller.
 */
@Composable
private fun <T> CapabilityListScreen(
    contentPadding: PaddingValues,
    title: String,
    intro: String,
    uiState: HermesCourierUiState,
    listing: HermesCapabilityListing<T>,
    onRefresh: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
    emptyHint: String,
    itemKey: (T) -> String,
    itemRenderer: @Composable (T) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CompactStatusStrip(
                uiState = uiState,
                onReconnect = onReconnectRealtime,
                onRetryQueued = onRetryQueuedApprovalActions,
            )
        }
        item {
            Card(elevation = courierCardElevation()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = intro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = uiState.libraryStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onRefresh) {
                        Text(text = if (uiState.libraryLoading) "Refreshing…" else "Refresh library")
                    }
                }
            }
        }
        val unavailable = listing.unavailable
        if (unavailable != null) {
            item { UnavailableCard(unavailable.type, unavailable.detail, unavailable.endpoint, unavailable.fallbackPollEndpoints) }
        } else if (listing.items.isEmpty()) {
            item {
                if (uiState.libraryLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Card(elevation = courierCardElevation()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "No items yet", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = emptyHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = onRefresh) { Text("Refresh library") }
                        }
                    }
                }
            }
        } else {
            items(items = listing.items, key = { item -> itemKey(item) }) { item -> itemRenderer(item) }
        }
    }
}

@Composable
private fun UnavailableCard(
    type: String,
    detail: String,
    endpoint: String?,
    fallbackPollEndpoints: List<String>,
) {
    Card(
        elevation = courierCardElevation(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Gateway declared this capability unavailable",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = detail.ifBlank { "No further detail provided by the gateway." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (!endpoint.isNullOrBlank()) {
                Text(
                    text = "Endpoint: $endpoint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (fallbackPollEndpoints.isNotEmpty()) {
                Text(
                    text = "Fallback endpoints: ${fallbackPollEndpoints.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = "Type: $type",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ExpandableCard(
    summary: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(elevation = courierCardElevation()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            summary()
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(text = if (expanded) "Hide details" else "Show details")
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                detail()
            }
        }
    }
}

@Composable
fun SkillsScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    onRefresh: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
) {
    CapabilityListScreen(
        contentPadding = contentPadding,
        title = "Skills",
        intro = "Skills and tools the agent can invoke. Read-only in this release.",
        uiState = uiState,
        listing = uiState.skills,
        onRefresh = onRefresh,
        onReconnectRealtime = onReconnectRealtime,
        onRetryQueuedApprovalActions = onRetryQueuedApprovalActions,
        emptyHint = "The gateway returned an empty skills list. Ask the Hermes backend to register a skill, then refresh.",
        itemKey = { it.skillId },
        itemRenderer = { skill -> SkillRow(skill) },
    )
}

@Composable
private fun SkillRow(skill: HermesSkill) {
    ExpandableCard(
        summary = {
            Text(text = skill.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (skill.enabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.labelSmall,
                color = if (skill.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (skill.description.isNotBlank()) {
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        detail = {
            Text(text = "ID: ${skill.skillId}", style = MaterialTheme.typography.bodySmall)
            if (!skill.version.isNullOrBlank()) {
                Text(text = "Version: ${skill.version}", style = MaterialTheme.typography.bodySmall)
            }
            if (!skill.lastUsedAt.isNullOrBlank()) {
                Text(text = "Last used: ${skill.lastUsedAt}", style = MaterialTheme.typography.bodySmall)
            }
            if (skill.scopes.isNotEmpty()) {
                ScopeRow(labels = skill.scopes)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopeRow(labels: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { scope ->
            AssistChip(
                onClick = {},
                label = { Text(scope) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

@Composable
fun MemoryScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    onRefresh: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
) {
    CapabilityListScreen(
        contentPadding = contentPadding,
        title = "Memory",
        intro = "Memory snippets the agent keeps between sessions. Read-only in this release.",
        uiState = uiState,
        listing = uiState.memory,
        onRefresh = onRefresh,
        onReconnectRealtime = onReconnectRealtime,
        onRetryQueuedApprovalActions = onRetryQueuedApprovalActions,
        emptyHint = "No memory entries returned yet. This surface is read-only.",
        itemKey = { it.memoryId },
        itemRenderer = { item -> MemoryRow(item) },
    )
}

@Composable
private fun MemoryRow(item: HermesMemoryItem) {
    ExpandableCard(
        summary = {
            Text(text = item.title, style = MaterialTheme.typography.titleSmall)
            if (item.snippet.isNotBlank()) {
                Text(
                    text = item.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.updatedAt.isNotBlank()) {
                Text(
                    text = "Updated: ${item.updatedAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        detail = {
            Text(text = "ID: ${item.memoryId}", style = MaterialTheme.typography.bodySmall)
            if (!item.body.isNullOrBlank()) {
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (item.pinned) {
                Text(
                    text = "Pinned",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (item.tags.isNotEmpty()) {
                ScopeRow(labels = item.tags)
            }
        },
    )
}

@Composable
fun CronScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    onRefresh: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
) {
    CapabilityListScreen(
        contentPadding = contentPadding,
        title = "Scheduled jobs",
        intro = "Scheduled tasks (cron-style) managed by the agent. Read-only in this release.",
        uiState = uiState,
        listing = uiState.cronJobs,
        onRefresh = onRefresh,
        onReconnectRealtime = onReconnectRealtime,
        onRetryQueuedApprovalActions = onRetryQueuedApprovalActions,
        emptyHint = "No scheduled jobs returned yet.",
        itemKey = { it.cronId },
        itemRenderer = { job -> CronRow(job) },
    )
}

@Composable
private fun CronRow(job: HermesCronJob) {
    ExpandableCard(
        summary = {
            Text(text = job.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${if (job.enabled) "Enabled" else "Disabled"} · ${job.schedule.ifBlank { "no schedule" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!job.nextRunAt.isNullOrBlank()) {
                Text(
                    text = "Next run: ${job.nextRunAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        detail = {
            Text(text = "ID: ${job.cronId}", style = MaterialTheme.typography.bodySmall)
            if (job.description.isNotBlank()) {
                Text(text = job.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (!job.lastRunAt.isNullOrBlank()) {
                Text(text = "Last run: ${job.lastRunAt}", style = MaterialTheme.typography.bodySmall)
            }
            if (!job.lastStatus.isNullOrBlank()) {
                Text(text = "Last status: ${job.lastStatus}", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
fun LogsScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    onRefresh: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
) {
    CapabilityListScreen(
        contentPadding = contentPadding,
        title = "Activity log",
        intro = "Recent log and activity entries reported by the gateway.",
        uiState = uiState,
        listing = uiState.logs,
        onRefresh = onRefresh,
        onReconnectRealtime = onReconnectRealtime,
        onRetryQueuedApprovalActions = onRetryQueuedApprovalActions,
        emptyHint = "No log entries returned yet.",
        itemKey = { it.logId },
        itemRenderer = { entry -> LogRow(entry) },
    )
}

@Composable
private fun LogRow(entry: HermesLogEntry) {
    val severityColor = when (entry.severity.lowercase()) {
        "error" -> MaterialTheme.colorScheme.error
        "warn", "warning" -> MaterialTheme.colorScheme.tertiary
        "debug" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Card(elevation = courierCardElevation()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.severity.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = severityColor,
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
            )
            val tail = listOfNotNull(
                entry.timestamp.takeIf { it.isNotBlank() },
                entry.source?.takeIf { it.isNotBlank() },
                entry.sessionId?.takeIf { it.isNotBlank() }?.let { "session $it" },
            ).joinToString(" · ")
            if (tail.isNotBlank()) {
                Text(
                    text = tail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

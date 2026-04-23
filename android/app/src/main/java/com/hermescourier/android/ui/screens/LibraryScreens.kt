package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    capabilityLabel: String,
    uiState: HermesCourierUiState,
    listing: HermesCapabilityListing<T>,
    onRefresh: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
    emptyHint: String,
    itemKey: (T) -> String,
    itemRenderer: @Composable (T) -> Unit,
    headerAction: @Composable (() -> Unit)? = null,
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
        val unavailable = listing.unavailable
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
                    // Suppress the generic "Library loaded" status line when the
                    // gateway has explicitly declared this capability unavailable —
                    // the dedicated card below says what the user needs to know.
                    if (unavailable == null) {
                        Text(
                            text = uiState.libraryStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRefresh) {
                                Text(text = if (uiState.libraryLoading) "Refreshing…" else "Refresh library")
                            }
                            headerAction?.invoke()
                        }
                    }
                }
            }
        }
        if (unavailable != null) {
            item { UnavailableCard(capabilityLabel = capabilityLabel) }
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
private fun UnavailableCard(capabilityLabel: String) {
    // Neutral "not enabled on this gateway" treatment. This is just an optional,
    // unsupported backend feature — no need for a giant red warning page.
    Card(
        elevation = courierCardElevation(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Not enabled on this gateway",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "The connected backend does not expose $capabilityLabel. It will appear here automatically if the gateway starts supporting it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onSaveSkill: (name: String, content: String, category: String) -> Unit,
    onDeleteSkill: (name: String) -> Unit,
    onFetchSkillContent: suspend (name: String, category: String) -> String?,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editSkill by rememberSaveable { mutableStateOf<HermesSkill?>(null) }
    var editSkillContent by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteSkill by rememberSaveable { mutableStateOf<HermesSkill?>(null) }

    CapabilityListScreen(
        contentPadding = contentPadding,
        title = "Skills",
        intro = "Skills and tools the agent can invoke.",
        capabilityLabel = "skills",
        uiState = uiState,
        listing = uiState.skills,
        onRefresh = onRefresh,
        onReconnectRealtime = onReconnectRealtime,
        onRetryQueuedApprovalActions = onRetryQueuedApprovalActions,
        emptyHint = "The gateway returned an empty skills list. Tap \"+ New skill\" to create one.",
        itemKey = { it.skillId },
        itemRenderer = { skill ->
            SkillRow(
                skill = skill,
                onEdit = { editSkill = skill },
                onDelete = { deleteSkill = skill },
            )
        },
        headerAction = {
            Button(onClick = { showCreateDialog = true }) { Text("+ New skill") }
        },
    )

    if (showCreateDialog) {
        SkillEditDialog(
            title = "New skill",
            initialName = "",
            initialContent = "",
            initialCategory = "",
            onSave = { name, content, category ->
                onSaveSkill(name, content, category)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    val editing = editSkill
    if (editing != null) {
        LaunchedEffect(editing.skillId) {
            editSkillContent = onFetchSkillContent(editing.name, "")
        }
        SkillEditDialog(
            title = "Edit skill",
            initialName = editing.name,
            initialContent = editSkillContent ?: editing.description,
            initialCategory = "",
            onSave = { name, content, category ->
                onSaveSkill(name, content, category)
                editSkill = null
                editSkillContent = null
            },
            onDismiss = { editSkill = null; editSkillContent = null },
        )
    }

    val deleting = deleteSkill
    if (deleting != null) {
        SkillDeleteConfirmDialog(
            skillName = deleting.name,
            onConfirm = {
                onDeleteSkill(deleting.name)
                deleteSkill = null
            },
            onDismiss = { deleteSkill = null },
        )
    }
}

@Composable
private fun SkillRow(
    skill: HermesSkill,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
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
private fun SkillEditDialog(
    title: String,
    initialName: String,
    initialContent: String,
    initialCategory: String,
    onSave: (name: String, content: String, category: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var content by rememberSaveable { mutableStateOf(initialContent) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Skill name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Skill content (SKILL.md)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    singleLine = false,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { onSave(name.trim(), content, category.trim()) },
                        enabled = name.trim().isNotBlank() && content.isNotBlank(),
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun SkillDeleteConfirmDialog(
    skillName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Delete skill?", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "This will permanently remove \"$skillName\" from the gateway. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onConfirm) { Text("Delete") }
                }
            }
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
        capabilityLabel = "memory",
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
        capabilityLabel = "scheduled jobs",
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
        capabilityLabel = "activity logs",
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

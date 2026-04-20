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
import androidx.compose.material3.AlertDialog
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
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.ui.approvalCardSummary
import com.hermescourier.android.ui.approvalStatusBadge

private val ApprovalFilters = listOf("All", "Biometrics required", "Standard review")

@Composable
fun ApprovalsScreen(
    contentPadding: PaddingValues,
    approvals: List<HermesApprovalSummary>,
    onApproveApproval: (String, String?) -> Unit,
    onRejectApproval: (String, String?) -> Unit,
    onOpenApprovalDetail: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("All") }
    var selectedApprovalId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAction by rememberSaveable { mutableStateOf<String?>(null) }
    var noteDraft by rememberSaveable { mutableStateOf("") }
    var actionMenuApprovalId by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    val filteredApprovals = remember(approvals, searchQuery, statusFilter) {
        approvals.filter { approval -> approvalMatchesFilter(approval, searchQuery, statusFilter) }
    }
    val biometricsRequiredCount = filteredApprovals.count { it.requiresBiometrics }
    val standardReviewCount = filteredApprovals.size - biometricsRequiredCount
    val selectedApproval = selectedApprovalId?.let { id -> approvals.firstOrNull { it.approvalId == id } }
    val actionMenuApproval = actionMenuApprovalId?.let { id -> approvals.firstOrNull { it.approvalId == id } }

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
                Text(text = "Approvals", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Filter, search, and long-press approvals to jump straight into decisions or copy the ID.",
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
            label = { Text(text = "Search approvals") },
            placeholder = { Text(text = "Title, detail, ID, or summary") },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ApprovalFilters.forEach { filter ->
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
                value = filteredApprovals.size.toString(),
                caption = "Matching filters",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Biometrics",
                value = biometricsRequiredCount.toString(),
                caption = "Need a trusted device",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Standard",
                value = standardReviewCount.toString(),
                caption = "Can be reviewed normally",
            )
        }

        if (filteredApprovals.isEmpty()) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "No approvals match your filters", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Try another search term or switch back to All to see every pending item.")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filteredApprovals, key = { it.approvalId }) { approval ->
                    ApprovalListCard(
                        approval = approval,
                        onOpenApprovalDetail = onOpenApprovalDetail,
                        onLongPress = { actionMenuApprovalId = approval.approvalId },
                    )
                }
            }
        }
    }

    if (actionMenuApproval != null) {
        ApprovalQuickActionsDialog(
            approval = actionMenuApproval,
            onApprove = {
                actionMenuApprovalId = null
                selectedApprovalId = actionMenuApproval.approvalId
                selectedAction = "approve"
                noteDraft = ""
            },
            onReject = {
                actionMenuApprovalId = null
                selectedApprovalId = actionMenuApproval.approvalId
                selectedAction = "reject"
                noteDraft = ""
            },
            onOpenDetails = {
                actionMenuApprovalId = null
                onOpenApprovalDetail(actionMenuApproval.approvalId)
            },
            onCopyApprovalId = {
                clipboardManager.setText(AnnotatedString(actionMenuApproval.approvalId))
                actionMenuApprovalId = null
            },
            onDismiss = { actionMenuApprovalId = null },
        )
    }

    if (selectedApproval != null && selectedAction != null) {
        val action = selectedAction!!
        ApprovalNoteDialog(
            approval = selectedApproval,
            action = action,
            noteDraft = noteDraft,
            onNoteChange = { noteDraft = it },
            onConfirm = {
                val approvalId = selectedApproval.approvalId
                val note = noteDraft.trim().ifEmpty { null }
                when (action) {
                    "approve" -> onApproveApproval(approvalId, note)
                    "reject" -> onRejectApproval(approvalId, note)
                }
                selectedApprovalId = null
                selectedAction = null
                noteDraft = ""
            },
            onDismiss = {
                selectedApprovalId = null
                selectedAction = null
                noteDraft = ""
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApprovalListCard(
    approval: HermesApprovalSummary,
    onOpenApprovalDetail: (String) -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpenApprovalDetail(approval.approvalId) },
                onLongClick = onLongPress,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = approvalStatusBadge(approval), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(text = approval.title, style = MaterialTheme.typography.titleMedium)
            Text(text = approval.detail, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = approvalCardSummary(approval),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApprovalQuickActionsDialog(
    approval: HermesApprovalSummary,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenDetails: () -> Unit,
    onCopyApprovalId: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Approval quick actions", style = MaterialTheme.typography.titleLarge)
                Text(text = approval.title, style = MaterialTheme.typography.bodyMedium)
                Text(text = approval.approvalId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) { Text(text = "Approve") }
                Button(onClick = onReject, modifier = Modifier.fillMaxWidth()) { Text(text = "Reject") }
                OutlinedButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) { Text(text = "Open details") }
                OutlinedButton(onClick = onCopyApprovalId, modifier = Modifier.fillMaxWidth()) { Text(text = "Copy approval ID") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(text = "Dismiss") }
            }
        }
    }
}

@Composable
private fun ApprovalNoteDialog(
    approval: HermesApprovalSummary,
    action: String,
    noteDraft: String,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${action.replaceFirstChar { it.uppercaseChar() }} approval") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = approval.title)
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Optional note") },
                    placeholder = { Text(text = "Add context for the reviewer") },
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = action.replaceFirstChar { it.uppercaseChar() })
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
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

private fun approvalMatchesFilter(
    approval: HermesApprovalSummary,
    query: String,
    filter: String,
): Boolean {
    val normalizedQuery = query.trim().lowercase()
    val searchable = listOf(
        approval.approvalId,
        approval.title,
        approval.detail,
        approvalCardSummary(approval),
        approvalStatusBadge(approval),
    ).joinToString(" ") { it.lowercase() }

    val matchesQuery = normalizedQuery.isBlank() || searchable.contains(normalizedQuery)
    val matchesFilter = when (filter) {
        "All" -> true
        "Biometrics required" -> approval.requiresBiometrics
        "Standard review" -> !approval.requiresBiometrics
        else -> true
    }

    return matchesQuery && matchesFilter
}

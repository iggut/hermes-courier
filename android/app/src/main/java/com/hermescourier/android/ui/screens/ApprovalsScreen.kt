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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesCourierUiState
import com.hermescourier.android.ui.CourierEmptyStateKind
import com.hermescourier.android.ui.approvalCardSummary
import com.hermescourier.android.ui.approvalEmptyStateMessage
import com.hermescourier.android.ui.approvalEmptyStateTitle
import com.hermescourier.android.ui.approvalStatusBadge
import com.hermescourier.android.ui.components.CompactStatusStrip
import com.hermescourier.android.ui.courierCardElevation
import com.hermescourier.android.ui.courierEmptyStateIllustration

private val ApprovalFilters = listOf("All", "Biometrics required", "Standard review")

@Composable
fun ApprovalsScreen(
    contentPadding: PaddingValues,
    uiState: HermesCourierUiState,
    approvals: List<HermesApprovalSummary>,
    queuedApprovalActions: Int,
    approvalActionStatus: String,
    onApproveApproval: (String, String?) -> Unit,
    onRejectApproval: (String, String?) -> Unit,
    onOpenApprovalDetail: (String) -> Unit,
    onRefresh: () -> Unit,
    onReconnectRealtime: () -> Unit,
    onRetryQueuedApprovalActions: () -> Unit,
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
            placeholder = { Text(text = "Search approvals") },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ApprovalFilters.forEach { filter ->
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
                text = "${filteredApprovals.size} visible",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (biometricsRequiredCount > 0) {
                Text(
                    text = "· $biometricsRequiredCount biometrics",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (standardReviewCount > 0) {
                Text(
                    text = "· $standardReviewCount standard",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (queuedApprovalActions > 0) {
                Text(
                    text = "· $queuedApprovalActions queued",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (filteredApprovals.isEmpty()) {
            Card(elevation = courierCardElevation()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        courierEmptyStateIllustration(
                            kind = CourierEmptyStateKind.Approvals,
                            modifier = Modifier.size(64.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = approvalEmptyStateTitle(searchQuery),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = approvalEmptyStateMessage(searchQuery),
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
                items(filteredApprovals, key = { it.approvalId }) { approval ->
                    ApprovalListCard(
                        approval = approval,
                        onApprove = { onApproveApproval(approval.approvalId, null) },
                        onReject = { onRejectApproval(approval.approvalId, null) },
                        onOpenApprovalDetail = onOpenApprovalDetail,
                        onLongPress = { actionMenuApprovalId = approval.approvalId },
                    )
                }
            }
        }

        if (approvalActionStatus.isNotBlank() && approvalActionStatus != "idle") {
            Text(
                text = approvalActionStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

private fun approvalMatchesFilter(
    approval: HermesApprovalSummary,
    query: String,
    filter: String,
): Boolean {
    val text = buildString {
        append(approval.approvalId)
        append(' ')
        append(approval.title)
        append(' ')
        append(approval.detail)
        append(' ')
        append(approvalCardSummary(approval))
    }.lowercase()
    val queryMatches = query.isBlank() || text.contains(query.trim().lowercase())

    val filterMatches = when (filter) {
        "All" -> true
        "Biometrics required" -> approval.requiresBiometrics
        "Standard review" -> !approval.requiresBiometrics
        else -> true
    }
    return queryMatches && filterMatches
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ApprovalListCard(
    approval: HermesApprovalSummary,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenApprovalDetail: (String) -> Unit,
    onLongPress: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { target ->
        when (target) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onApprove()
                true
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onReject()
                true
            }
            SwipeToDismissBoxValue.Settled -> false
        }
    })

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isActivelySwiping = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            val swipeProgress = if (isActivelySwiping) {
                FastOutSlowInEasing.transform(dismissState.progress.coerceIn(0f, 1f))
            } else 0f
            val isApproveAction = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
            val actionLabel = if (isApproveAction) "Approve" else "Reject"
            val actionIcon = if (isApproveAction) Icons.Filled.CheckCircle else Icons.Filled.Close
            val actionContainerColor = lerp(
                androidx.compose.ui.graphics.Color.Transparent,
                if (isApproveAction) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                swipeProgress,
            )
            val actionContentColor = lerp(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0f),
                if (isApproveAction) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                swipeProgress,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(actionContainerColor)
                    .padding(horizontal = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
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
                    Text(
                        text = if (isApproveAction) "→" else "←",
                        style = MaterialTheme.typography.titleLarge,
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
                    onClick = { onOpenApprovalDetail(approval.approvalId) },
                    onLongClick = onLongPress,
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = approval.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = approvalStatusBadge(approval),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = approval.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
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
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = approval.title, style = MaterialTheme.typography.titleLarge)
                Text(text = approval.detail, style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) { Text(text = "Approve") }
                    OutlinedButton(onClick = onReject, modifier = Modifier.fillMaxWidth()) { Text(text = "Reject") }
                    OutlinedButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) { Text(text = "Open details") }
                    OutlinedButton(onClick = onCopyApprovalId, modifier = Modifier.fillMaxWidth()) { Text(text = "Copy approval ID") }
                }
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
        title = { Text(text = "${action.take(1).uppercase()}${action.drop(1)} approval") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Add an optional note before you ${action} ${approval.approvalId}.")
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Note") },
                    placeholder = { Text(text = "Reason, context, or follow-up") },
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = approvalStatusButtonLabel(action))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

private fun approvalStatusButtonLabel(action: String): String = when (action.lowercase()) {
    "approve" -> "Approve"
    "reject" -> "Reject"
    else -> action.replaceFirstChar { it.uppercaseChar() }
}

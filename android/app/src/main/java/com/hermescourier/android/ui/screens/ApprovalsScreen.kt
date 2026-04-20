package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.ui.approvalCardSummary
import com.hermescourier.android.ui.approvalStatusBadge

@Composable
fun ApprovalsScreen(
    contentPadding: PaddingValues,
    approvals: List<HermesApprovalSummary>,
    onApproveApproval: (String, String?) -> Unit,
    onRejectApproval: (String, String?) -> Unit,
    onOpenApprovalDetail: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var selectedApprovalId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAction by rememberSaveable { mutableStateOf<String?>(null) }
    var noteDraft by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Approvals", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Review pending approvals and attach a secure note before sending a decision.")

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Approval snapshot", style = MaterialTheme.typography.titleMedium)
                Text(text = when {
                    approvals.isEmpty() -> "No approvals are waiting right now."
                    else -> "${approvals.size} approval${if (approvals.size == 1) "" else "s"} need attention."
                })
                Button(onClick = onRefresh) { Text(text = "Refresh") }
            }
        }

        if (approvals.isEmpty()) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "No pending approvals", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "When the gateway assigns items, they appear here. Use Refresh on the dashboard or settings if the list looks stale.",
                    )
                    OutlinedButton(onClick = onRefresh) { Text(text = "Try again") }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(approvals, key = { it.approvalId }) { approval ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = approval.title, style = MaterialTheme.typography.titleMedium)
                            Text(text = approvalCardSummary(approval), style = MaterialTheme.typography.bodyMedium)
                            Text(text = approvalStatusBadge(approval), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(text = "Approval ID: ${approval.approvalId}", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    selectedApprovalId = approval.approvalId
                                    selectedAction = "approve"
                                    noteDraft = ""
                                }) { Text(text = "Approve") }
                                Button(onClick = {
                                    selectedApprovalId = approval.approvalId
                                    selectedAction = "deny"
                                    noteDraft = ""
                                }) { Text(text = "Reject") }
                            }
                            OutlinedButton(onClick = { onOpenApprovalDetail(approval.approvalId) }) {
                                Text(text = "Open details")
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedApprovalId != null && selectedAction != null) {
        AlertDialog(
            onDismissRequest = {
                selectedApprovalId = null
                selectedAction = null
                noteDraft = ""
            },
            title = { Text(text = if (selectedAction == "approve") "Approve approval" else "Reject approval") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Add a short comment before sending the decision.")
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "Note / comment") },
                        placeholder = { Text(text = "Optional rationale, owner, or follow-up") },
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val approvalId = selectedApprovalId
                    val action = selectedAction
                    val note = noteDraft.trim().takeIf { it.isNotBlank() }
                    if (approvalId != null && action != null) {
                        if (action == "approve") {
                            onApproveApproval(approvalId, note)
                        } else {
                            onRejectApproval(approvalId, note)
                        }
                    }
                    selectedApprovalId = null
                    selectedAction = null
                    noteDraft = ""
                }) { Text(text = "Send") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    selectedApprovalId = null
                    selectedAction = null
                    noteDraft = ""
                }) { Text(text = "Cancel") }
            },
        )
    }
}

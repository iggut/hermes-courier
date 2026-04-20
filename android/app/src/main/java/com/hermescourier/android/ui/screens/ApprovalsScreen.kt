package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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

@Composable
fun ApprovalsScreen(
    contentPadding: PaddingValues,
    approvals: List<HermesApprovalSummary>,
    onApproveApproval: (String, String?) -> Unit,
    onRejectApproval: (String, String?) -> Unit,
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(approvals, key = { it.approvalId }) { approval ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = approval.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = approval.detail)
                        Text(text = if (approval.requiresBiometrics) "Biometrics required" else "Biometrics optional")
                        Text(text = "Approval ID: ${approval.approvalId}", style = MaterialTheme.typography.labelSmall)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                selectedApprovalId = approval.approvalId
                                selectedAction = "approve"
                                noteDraft = ""
                            }) {
                                Text(text = "Approve with note")
                            }
                            Button(onClick = {
                                selectedApprovalId = approval.approvalId
                                selectedAction = "reject"
                                noteDraft = ""
                            }) {
                                Text(text = "Reject with note")
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
                }) {
                    Text(text = "Send")
                }
            },
            dismissButton = {
                Button(onClick = {
                    selectedApprovalId = null
                    selectedAction = null
                    noteDraft = ""
                }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.hermescourier.android.ui.approvalDetailSubtitle
import com.hermescourier.android.ui.courierHeroCardElevation

@Composable
fun ApprovalDetailScreen(
    contentPadding: PaddingValues,
    approval: HermesApprovalSummary,
    onApproveApproval: (String, String?) -> Unit,
    onRejectApproval: (String, String?) -> Unit,
) {
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    var noteDraft by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Approval details", style = MaterialTheme.typography.headlineMedium)
        Text(text = approval.title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = approvalDetailSubtitle(approval),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            elevation = courierHeroCardElevation(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Approval overview", style = MaterialTheme.typography.titleMedium)
                Text(text = approval.detail)
                Text(text = if (approval.requiresBiometrics) "Biometrics are required for this decision." else "Biometrics are optional for this decision.")
                Text(text = "Approval ID: ${approval.approvalId}")
                Text(text = approvalCardSummary(approval), style = MaterialTheme.typography.bodySmall)
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Decision actions", style = MaterialTheme.typography.titleMedium)
                Text(text = "Open a note sheet to send an approval decision to the live gateway. The note is optional, but it gives the backend a richer audit trail.")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            pendingAction = "approve"
                            noteDraft = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Approve with note")
                    }
                    Button(
                        onClick = {
                            pendingAction = "reject"
                            noteDraft = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Reject with note")
                    }
                }
                OutlinedButton(
                    onClick = { onApproveApproval(approval.approvalId, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Approve now")
                }
                OutlinedButton(
                    onClick = { onRejectApproval(approval.approvalId, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Reject now")
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Quick guidance", style = MaterialTheme.typography.titleMedium)
                Text(text = "• Use the detail screen when you want a focused read before deciding.")
                Text(text = "• Return to the approvals list if you want to compare multiple pending approvals.")
                Text(text = "• Biometrics-required items are best handled on a trusted device.")
            }
        }
    }

    if (pendingAction != null) {
        val action = pendingAction!!
        ApprovalDecisionDialog(
            approval = approval,
            action = action,
            noteDraft = noteDraft,
            onNoteChange = { noteDraft = it },
            onConfirm = {
                val trimmedNote = noteDraft.trim().ifEmpty { null }
                when (action) {
                    "approve" -> onApproveApproval(approval.approvalId, trimmedNote)
                    "reject" -> onRejectApproval(approval.approvalId, trimmedNote)
                }
                pendingAction = null
                noteDraft = ""
            },
            onDismiss = {
                pendingAction = null
                noteDraft = ""
            },
        )
    }
}

@Composable
private fun ApprovalDecisionDialog(
    approval: HermesApprovalSummary,
    action: String,
    noteDraft: String,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (action == "approve") "Approve approval" else "Reject approval")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "${if (action == "approve") "Approve" else "Reject"} — ${approval.title}")
                Text(text = "Add a short comment before sending the decision to the gateway.")
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Note / comment") },
                    placeholder = { Text(text = "Optional note for the audit trail") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = if (action == "approve") "Send approval" else "Send rejection")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

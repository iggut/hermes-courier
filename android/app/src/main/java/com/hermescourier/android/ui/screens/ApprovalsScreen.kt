package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesApprovalSummary

@Composable
fun ApprovalsScreen(
    contentPadding: PaddingValues,
    approvals: List<HermesApprovalSummary>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Approvals", style = MaterialTheme.typography.headlineMedium)
                Text(text = "Review and submit zero-trust approval decisions from your device.")
            }
        }
        items(approvals) { approval ->
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                    Text(text = approval.title, style = MaterialTheme.typography.titleMedium)
                    Text(text = approval.detail, style = MaterialTheme.typography.bodyMedium)
                    Text(text = if (approval.requiresBiometrics) "Biometrics required" else "Biometrics optional", style = MaterialTheme.typography.bodySmall)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApprove(approval.approvalId) }) { Text(text = "Approve") }
                        Button(onClick = { onReject(approval.approvalId) }) { Text(text = "Reject") }
                    }
                }
            }
        }
        if (approvals.isEmpty()) {
            item {
                Text(text = "No approvals are waiting right now.")
            }
        }
    }
}

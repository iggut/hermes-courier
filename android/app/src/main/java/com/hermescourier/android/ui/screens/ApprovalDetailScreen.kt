package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

        Card(elevation = courierHeroCardElevation(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
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
                Text(text = "You can approve or reject directly from this detail view. Add context in the approval sheet if you need to leave a note.")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onApproveApproval(approval.approvalId, null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Approve now")
                    }
                    Button(onClick = { onRejectApproval(approval.approvalId, null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Reject now")
                    }
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Quick guidance", style = MaterialTheme.typography.titleMedium)
                Text(text = "• Use the detail screen when you want a focused read before deciding.")
                Text(text = "• Return to the approvals list if you want to compare multiple pending approvals.")
                Text(text = "• biometrics-required items are best handled on a trusted device.")
                OutlinedButton(onClick = { onApproveApproval(approval.approvalId, null) }, modifier = Modifier.fillMaxWidth()) { Text(text = "Approve without note") }
            }
        }
    }
}

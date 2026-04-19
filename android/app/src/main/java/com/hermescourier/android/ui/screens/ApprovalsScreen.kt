
package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermescourier.android.ui.components.HermesCard
import com.hermescourier.android.ui.components.PrimaryActionButton
import com.hermescourier.android.ui.components.SectionTitle

@Composable
fun ApprovalsScreen(contentPadding: PaddingValues) {
    val approvals = listOf(
        "Approve sending a message to Slack #ops?",
        "Allow Hermes to restart a long-running task?",
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle(title = "Approvals", subtitle = "Biometric-gated consent for sensitive actions.") }
        items(approvals) { approval ->
            HermesCard(title = "Pending approval", body = approval)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryActionButton(text = "Require biometrics") { }
                PrimaryActionButton(text = "Review policy") { }
            }
        }
        item {
            Text(
                text = "Approvals will eventually map to secure, device-bound signatures.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

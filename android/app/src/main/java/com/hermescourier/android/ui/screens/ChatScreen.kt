
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
import com.hermescourier.android.ui.components.SectionTitle

@Composable
fun ChatScreen(contentPadding: PaddingValues) {
    val messages = listOf(
        "Hermes: Awaiting your next instruction.",
        "You: Review the latest tool approvals.",
        "Hermes: I found 2 pending approval requests.",
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle(title = "Live chat", subtitle = "Stream responses and tool events from Hermes.") }
        items(messages) { message ->
            HermesCard(title = "Conversation", body = message)
        }
        item {
            Text(
                text = "Next: voice input, attachments, and streaming tool execution logs.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

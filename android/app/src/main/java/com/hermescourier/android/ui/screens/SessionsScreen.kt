
package com.hermescourier.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.ui.components.HermesCard
import com.hermescourier.android.ui.components.SectionTitle

@Composable
fun SessionsScreen(contentPadding: PaddingValues, sessions: List<HermesSessionSummary>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle(title = "Sessions", subtitle = "Browse active and historical Hermes runs.") }
        items(sessions) { session ->
            HermesCard(title = session.title, body = session.status, trailing = session.updatedAt)
        }
        item {
            Text(
                text = "Future features: filters, deep links, log tails, and offline snapshots.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

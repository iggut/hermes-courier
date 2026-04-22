package com.hermescourier.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesCourierUiState

/**
 * Compact single-row status strip used at the top of each screen.
 *
 * Goals:
 * - One line by default; no giant status card.
 * - Expands inline on tap to reveal connection detail, freshness, realtime, and queued counts.
 * - Primary action (Reconnect / Retry queued) is a small icon/text button, never wrapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactStatusStrip(
    uiState: HermesCourierUiState,
    onReconnect: (() -> Unit)? = null,
    onRetryQueued: (() -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val mode = uiState.gatewayConnectionMode.lowercase()
    val isLive = mode.contains("live") && !mode.contains("demo")
    val isChecking = mode.contains("checking")
    val isDemo = mode.contains("demo")
    val isUnavailable = mode.contains("unavailable")

    val (containerColor, dotColor, shortLabel) = when {
        isLive -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            Color(0xFF22C55E),
            "Live",
        )
        isChecking -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            Color(0xFFF59E0B),
            "Checking",
        )
        isDemo -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Color(0xFFF59E0B),
            "Demo",
        )
        isUnavailable -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Color(0xFFEF4444),
            "Offline",
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.outline,
            uiState.gatewayConnectionMode.take(18),
        )
    }

    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val latestOnReconnect by rememberUpdatedState(onReconnect)
    val latestOnRetryQueued by rememberUpdatedState(onRetryQueued)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = shortLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = freshnessShort(uiState),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = if (expanded) "Hide status details" else "Show status details",
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp, start = 18.dp, end = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = uiState.gatewayConnectionDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Realtime: ${uiState.streamStatus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.queuedApprovalActions > 0) {
                        Text(
                            text = "${uiState.queuedApprovalActions} approval action(s) queued offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    ) {
                        if (latestOnReconnect != null) {
                            TextButton(onClick = { latestOnReconnect?.invoke() }) {
                                Text(text = "Reconnect")
                            }
                        }
                        if (uiState.queuedApprovalActions > 0 && latestOnRetryQueued != null) {
                            TextButton(onClick = { latestOnRetryQueued?.invoke() }) {
                                Text(text = "Retry queued")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun freshnessShort(ui: HermesCourierUiState): String {
    val last = ui.dashboard.lastSyncLabel.ifBlank { "unknown" }
    val stream = ui.streamStatus
    val isUnsupported = stream.contains("unsupported", ignoreCase = true) ||
        stream.contains("unavailable", ignoreCase = true)
    return when {
        // Gateway explicitly said realtime is off; don't lie about "synced" just because
        // REST is live. Polling is the honest mode here.
        isUnsupported -> "polling — $last"
        stream.contains("connected", ignoreCase = true) &&
            !stream.contains("disconnected", ignoreCase = true) -> "synced $last"
        stream.contains("reconnecting", ignoreCase = true) -> "reconnecting — $last"
        stream.contains("disconnected", ignoreCase = true) -> "stale — $last"
        else -> last
    }
}

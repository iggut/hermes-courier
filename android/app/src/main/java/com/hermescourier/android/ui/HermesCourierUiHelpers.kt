package com.hermescourier.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesSessionSummary

internal enum class CourierEmptyStateKind {
    Dashboard,
    Sessions,
    Approvals,
}

private data class CourierEmptyStatePalette(
    val baseColor: Color,
    val haloColor: Color,
    val accentColor: Color,
    val icon: ImageVector,
    val badgeIcon: ImageVector,
    val contentColor: Color,
)

@Composable
internal fun courierHeroCardElevation() = CardDefaults.cardElevation(
    defaultElevation = 4.dp,
    pressedElevation = 8.dp,
)

@Composable
internal fun courierCardElevation() = CardDefaults.cardElevation(
    defaultElevation = 1.dp,
    pressedElevation = 3.dp,
)

@Composable
internal fun courierEmptyStateIllustration(
    kind: CourierEmptyStateKind,
    modifier: Modifier = Modifier,
) {
    val palette = when (kind) {
        CourierEmptyStateKind.Dashboard -> CourierEmptyStatePalette(
            baseColor = MaterialTheme.colorScheme.primaryContainer,
            haloColor = MaterialTheme.colorScheme.secondaryContainer,
            accentColor = MaterialTheme.colorScheme.tertiaryContainer,
            icon = Icons.Filled.Home,
            badgeIcon = Icons.Filled.Refresh,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        CourierEmptyStateKind.Sessions -> CourierEmptyStatePalette(
            baseColor = MaterialTheme.colorScheme.secondaryContainer,
            haloColor = MaterialTheme.colorScheme.primaryContainer,
            accentColor = MaterialTheme.colorScheme.surfaceVariant,
            icon = Icons.Filled.List,
            badgeIcon = Icons.Filled.Lock,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )

        CourierEmptyStateKind.Approvals -> CourierEmptyStatePalette(
            baseColor = MaterialTheme.colorScheme.tertiaryContainer,
            haloColor = MaterialTheme.colorScheme.primaryContainer,
            accentColor = MaterialTheme.colorScheme.errorContainer,
            icon = Icons.Filled.CheckCircle,
            badgeIcon = Icons.Filled.Lock,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }

    Box(
        modifier = modifier.size(84.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(palette.haloColor.copy(alpha = 0.3f)),
        )
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(palette.baseColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = palette.icon,
                contentDescription = null,
                tint = palette.contentColor,
                modifier = Modifier.size(34.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(palette.accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = palette.badgeIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

internal fun navigationLabel(baseLabel: String, count: Int): String =
    if (count > 0) "$baseLabel ($count)" else baseLabel

internal fun dashboardNextStep(
    bootstrapState: String,
    pendingApprovals: Int,
    activeSessions: Int,
): String = when {
    bootstrapState.contains("demo", ignoreCase = true) ->
        "Connect to a live gateway to see real sessions, approvals, and conversation activity."

    bootstrapState.contains("unavailable", ignoreCase = true) ->
        "Gateway access is unavailable. Open Settings to check the URL, certificate, and reconnect."

    pendingApprovals > 0 ->
        "Review $pendingApprovals pending approval${if (pendingApprovals == 1) "" else "s"} before they time out."

    activeSessions > 0 ->
        "Browse $activeSessions active session${if (activeSessions == 1) "" else "s"} and catch up on the latest activity."

    bootstrapState.contains("ready", ignoreCase = true) ->
        "You are connected. Refresh to pick up new sessions, approvals, and conversation events."

    bootstrapState.contains("negotiating", ignoreCase = true) ||
        bootstrapState.contains("bootstrapping", ignoreCase = true) ->
        "The gateway is negotiating a secure connection. This view will fill in as soon as it is ready."

    else ->
        "Refresh to fetch the latest gateway snapshot and live activity."
}

internal fun dashboardFreshnessLabel(
    lastSyncLabel: String,
    streamStatus: String,
): String = when {
    streamStatus.contains("disconnected", ignoreCase = true) || streamStatus.contains("unavailable", ignoreCase = true) ->
        "Data may be stale · Last update: $lastSyncLabel"
    streamStatus.contains("demo", ignoreCase = true) -> "Demo data · Last update: $lastSyncLabel"
    streamStatus.contains("connected", ignoreCase = true) -> "Live sync · Last update: $lastSyncLabel"
    else -> "Last update: $lastSyncLabel"
}

internal fun sessionStatusBadge(status: String): String = when {
    status.contains("active", ignoreCase = true) -> "Live session"
    status.contains("pending", ignoreCase = true) -> "Waiting"
    status.contains("completed", ignoreCase = true) -> "Completed"
    status.contains("error", ignoreCase = true) -> "Needs attention"
    else -> status.replaceFirstChar { it.uppercaseChar() }
}

internal fun sessionDetailSubtitle(session: HermesSessionSummary): String =
    "Updated ${session.updatedAt} · ${sessionStatusBadge(session.status)}"

internal fun approvalStatusBadge(approval: HermesApprovalSummary): String =
    if (approval.requiresBiometrics) "Biometrics required" else "Standard review"

internal fun approvalDetailSubtitle(approval: HermesApprovalSummary): String =
    "${approvalStatusBadge(approval)} · ${approval.approvalId}"

internal fun approvalActionLabel(action: String): String = when (action.trim().lowercase()) {
    "deny", "reject" -> "Reject"
    "approve" -> "Approve"
    else -> action.replaceFirstChar { it.uppercaseChar() }
}

internal fun sessionCardSummary(session: HermesSessionSummary): String =
    "${session.status} · ${session.updatedAt}"

internal fun approvalCardSummary(approval: HermesApprovalSummary): String =
    approval.detail

internal fun sessionEmptyStateTitle(filter: String, query: String): String = when {
    filter == "Archived" -> "No archived sessions yet"
    query.isNotBlank() -> "No sessions match your search"
    else -> "No sessions to show"
}

internal fun sessionEmptyStateMessage(filter: String, query: String): String = when {
    filter == "Archived" -> "Swipe left on any session card to archive it locally, then use this view to bring it back when you need it."
    query.isNotBlank() -> "Try a broader search term, clear the filter chips, or refresh from the top app bar."
    else -> "Live sessions appear here as soon as the gateway starts streaming them."
}

internal fun approvalEmptyStateTitle(query: String): String =
    if (query.isNotBlank()) "No approvals match your search" else "No approvals waiting right now"

internal fun approvalEmptyStateMessage(query: String): String = when {
    query.isNotBlank() -> "Try a broader search term, clear the filter chips, or refresh from the top app bar."
    else -> "When the gateway needs a decision, the request will appear here with quick approve/reject actions."
}

internal fun archiveHint(archivedCount: Int): String = when (archivedCount) {
    0 -> "Swipe left on a session card to archive it locally for later."
    1 -> "1 session is archived locally. Open Archived to restore it."
    else -> "$archivedCount sessions are archived locally. Open Archived to restore them."
}

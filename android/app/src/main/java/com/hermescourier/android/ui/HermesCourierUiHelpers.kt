package com.hermescourier.android.ui

import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesSessionSummary

@Composable
internal fun courierCardElevation() = CardDefaults.cardElevation(
    defaultElevation = 2.dp,
    pressedElevation = 6.dp,
)

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

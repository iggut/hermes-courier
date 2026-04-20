package com.hermescourier.android.ui

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

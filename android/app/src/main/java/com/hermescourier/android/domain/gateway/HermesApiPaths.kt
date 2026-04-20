package com.hermescourier.android.domain.gateway

/** Canonical paths aligned with [shared/contract/hermes-courier-api.yaml]. */
object HermesApiPaths {
    const val AUTH_CHALLENGE = "v1/auth/challenge"
    const val AUTH_RESPONSE = "v1/auth/response"
    const val DASHBOARD = "v1/dashboard"
    const val SESSIONS = "v1/sessions"
    const val APPROVALS = "v1/approvals"
    const val CONVERSATION = "v1/conversation"
    /** Mobile clients use a WebSocket at this path; the contract describes live events. */
    const val EVENTS_STREAM = "v1/events"

    fun approvalDecision(approvalId: String): String = "v1/approvals/$approvalId/decision"
}

package com.hermescourier.android.domain.gateway

import com.hermescourier.android.domain.model.HermesApprovalActionResult
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesAuthChallengeResponse
import com.hermescourier.android.domain.model.HermesAuthSession
import com.hermescourier.android.domain.model.HermesConversationEvent
import com.hermescourier.android.domain.model.HermesDashboardSnapshot
import com.hermescourier.android.domain.model.HermesRealtimeEnvelope
import com.hermescourier.android.domain.model.HermesSessionControlActionResult
import com.hermescourier.android.domain.model.HermesSessionSummary
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal fun String.toJsonObject(): JSONObject = JSONObject(this)

internal fun String.toJsonArrayOrObject(): JSONArray {
    val parsed = JSONTokener(this).nextValue()
    return when (parsed) {
        is JSONArray -> parsed
        is JSONObject -> parsed.optJSONArray("items")
            ?: parsed.optJSONArray("data")
            ?: parsed.optJSONArray("results")
            ?: JSONArray().put(parsed)
        else -> JSONArray()
    }
}

internal fun JSONObject.toChallenge(): HermesAuthChallengeResponse = HermesAuthChallengeResponse(
    challengeId = getString("challengeId"),
    nonce = getString("nonce"),
    expiresAt = optString("expiresAt", ""),
    trustLevel = optString("trustLevel", "unknown"),
)

internal fun JSONObject.toSession(): HermesAuthSession = HermesAuthSession(
    sessionId = getString("sessionId"),
    accessToken = getString("accessToken"),
    refreshToken = getString("refreshToken"),
    expiresAt = getString("expiresAt"),
    gatewayUrl = getString("gatewayUrl"),
    mtlsRequired = optBoolean("mtlsRequired", true),
    scope = optJSONArray("scope")?.toStringList() ?: emptyList(),
)

internal fun JSONObject.toDashboard(): HermesDashboardSnapshot = HermesDashboardSnapshot(
    activeSessionCount = optInt("activeSessionCount", 0),
    pendingApprovalCount = optInt("pendingApprovalCount", 0),
    lastSyncLabel = optString("lastSyncLabel", "Never"),
    connectionState = optString("connectionState", "Connected"),
)

internal fun JSONObject.toSessionSummary(): HermesSessionSummary = HermesSessionSummary(
    sessionId = optString("sessionId", optString("id", "session-unknown")),
    title = optString("title", "Untitled session"),
    status = optString("status", "unknown"),
    updatedAt = optString("updatedAt", "unknown"),
)

internal fun JSONObject.toApprovalSummary(): HermesApprovalSummary = HermesApprovalSummary(
    approvalId = optString("approvalId", optString("id", "approval-unknown")),
    title = optString("title", "Approval"),
    detail = optString("detail", ""),
    requiresBiometrics = optBoolean("requiresBiometrics", false),
)

internal fun JSONObject.toConversationEvent(): HermesConversationEvent = HermesConversationEvent(
    eventId = optString("eventId", optString("id", "event-unknown")),
    author = optString("author", "Hermes"),
    body = optString("body", optString("message", "")),
    timestamp = optString("timestamp", "now"),
)

internal fun String.toConversationEventOrNull(fallbackMessage: String): HermesConversationEvent? {
    if (isBlank()) {
        return HermesConversationEvent(
            eventId = "local-${System.currentTimeMillis()}",
            author = "You",
            body = fallbackMessage,
            timestamp = "now",
        )
    }
    return runCatching {
        val parsed = JSONTokener(this).nextValue()
        when (parsed) {
            is JSONObject -> parsed.toConversationEvent()
            is JSONArray -> parsed.optJSONObject(parsed.length() - 1)?.toConversationEvent()
            else -> null
        }
    }.getOrNull()
}

internal fun JSONObject.toApprovalActionResult(
    fallbackApprovalId: String,
    fallbackDecision: String,
): HermesApprovalActionResult = HermesApprovalActionResult(
    approvalId = optString("approvalId", fallbackApprovalId),
    action = optString("action", optString("decision", fallbackDecision)),
    status = optString("status", "submitted"),
    detail = optString("detail", optString("message", "Approval action submitted.")),
    updatedAt = optString("updatedAt", "now"),
)

internal fun JSONObject.toApprovalActionResult(): HermesApprovalActionResult = HermesApprovalActionResult(
    approvalId = optString("approvalId", "unknown"),
    action = optString("action", optString("decision", "unknown")),
    status = optString("status", "submitted"),
    detail = optString("detail", optString("message", "Approval action submitted.")),
    updatedAt = optString("updatedAt", "now"),
)

internal fun JSONObject.toRealtimeEnvelope(): HermesRealtimeEnvelope {
    val type = optString("type", optString("kind", "conversation"))
    return HermesRealtimeEnvelope(
        type = type,
        dashboard = optJSONObject("dashboard")?.toDashboard(),
        sessions = optJSONArray("sessions")?.toSessionList(),
        approvals = optJSONArray("approvals")?.toApprovalList(),
        conversation = optJSONObject("conversation")?.toConversationEvent()
            ?: optJSONObject("event")?.toConversationEvent()
            ?: if (has("body") || has("message")) toConversationEvent() else null,
        approvalResult = optJSONObject("approvalResult")?.toApprovalActionResult()
            ?: optJSONObject("approval_action")?.toApprovalActionResult(),
        sessionControlResult = optJSONObject("sessionControlResult")?.toSessionControlActionResult()
            ?: optJSONObject("session_control_action")?.toSessionControlActionResult(),
        eventId = optString("eventId", optString("id", "")).takeIf { it.isNotBlank() },
        eventTimestamp = optString("timestamp", "").takeIf { it.isNotBlank() },
    )
}

internal fun JSONObject.toSessionControlActionResult(
    fallback: HermesSessionControlActionResult? = null,
): HermesSessionControlActionResult {
    val endpointFromJson = if (has("endpoint")) {
        optString("endpoint", "").takeIf { it.isNotEmpty() }
    } else {
        null
    }
    return HermesSessionControlActionResult(
        sessionId = optString("sessionId", fallback?.sessionId ?: "unknown"),
        action = optString("action", fallback?.action ?: "unknown"),
        status = optString("status", fallback?.status ?: "submitted"),
        detail = optString("detail", optString("message", fallback?.detail ?: "Session-control action submitted.")),
        updatedAt = optString("updatedAt", fallback?.updatedAt ?: "now"),
        endpoint = endpointFromJson ?: fallback?.endpoint,
        supported = if (has("supported")) optBoolean("supported", true) else (fallback?.supported ?: true),
    )
}

internal fun JSONArray.toSessionList(): List<HermesSessionSummary> = mutableListOf<HermesSessionSummary>().apply {
    for (index in 0 until length()) add(getJSONObject(index).toSessionSummary())
}

internal fun JSONArray.toApprovalList(): List<HermesApprovalSummary> = mutableListOf<HermesApprovalSummary>().apply {
    for (index in 0 until length()) add(getJSONObject(index).toApprovalSummary())
}

internal fun JSONArray.toConversationList(): List<HermesConversationEvent> = mutableListOf<HermesConversationEvent>().apply {
    for (index in 0 until length()) add(getJSONObject(index).toConversationEvent())
}

internal fun JSONArray.toStringList(): List<String> = mutableListOf<String>().apply {
    for (index in 0 until length()) add(getString(index))
}

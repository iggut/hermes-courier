package com.hermescourier.android.domain.gateway

import com.hermescourier.android.domain.model.HermesApprovalActionResult
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesAuthChallengeResponse
import com.hermescourier.android.domain.model.HermesAuthSession
import com.hermescourier.android.domain.model.HermesCapabilityListing
import com.hermescourier.android.domain.model.HermesConversationEvent
import com.hermescourier.android.domain.model.HermesCronJob
import com.hermescourier.android.domain.model.HermesDashboardSnapshot
import com.hermescourier.android.domain.model.HermesLogEntry
import com.hermescourier.android.domain.model.HermesMemoryItem
import com.hermescourier.android.domain.model.HermesRealtimeEnvelope
import com.hermescourier.android.domain.model.HermesSessionControlActionResult
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.domain.model.HermesSkill
import com.hermescourier.android.domain.model.HermesUnavailablePayload
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
    sessionId = optString("sessionId", "").takeIf { it.isNotBlank() },
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

/**
 * Detects an `UnavailablePayload` body. Returns null when the body is a regular
 * response or when the payload is missing the `supported: false` signal. A
 * body with `type == *_unavailable` or explicit `supported == false` counts as
 * unavailable; `type: events_unavailable` is the canonical shape but we treat
 * any `*_unavailable` type the same way so the client does not need a case for
 * every future capability.
 */
internal fun JSONObject.toUnavailableOrNull(): HermesUnavailablePayload? {
    val type = optString("type", "")
    val hasExplicitSupported = has("supported")
    val supportedValue = if (hasExplicitSupported) optBoolean("supported", true) else true
    val looksUnavailable = type.endsWith("_unavailable") || (hasExplicitSupported && !supportedValue)
    if (!looksUnavailable) return null
    val fallback = optJSONArray("fallbackPollEndpoints")?.toStringList().orEmpty()
    return HermesUnavailablePayload(
        type = type.ifBlank { "unavailable" },
        detail = optString("detail", ""),
        endpoint = optString("endpoint", "").takeIf { it.isNotBlank() },
        fallbackPollEndpoints = fallback,
    )
}

internal fun JSONObject.toSkill(): HermesSkill = HermesSkill(
    skillId = optString("skillId", optString("id", "skill-unknown")),
    name = optString("name", "Untitled skill"),
    description = optString("description", ""),
    enabled = optBoolean("enabled", true),
    version = optString("version", "").takeIf { it.isNotBlank() },
    lastUsedAt = optString("lastUsedAt", "").takeIf { it.isNotBlank() },
    scopes = optJSONArray("scopes")?.toStringList() ?: emptyList(),
)

internal fun JSONObject.toMemoryItem(): HermesMemoryItem = HermesMemoryItem(
    memoryId = optString("memoryId", optString("id", "memory-unknown")),
    title = optString("title", optString("name", "Untitled memory")),
    snippet = optString("snippet", optString("summary", "")),
    body = optString("body", "").takeIf { it.isNotBlank() },
    tags = optJSONArray("tags")?.toStringList() ?: emptyList(),
    updatedAt = optString("updatedAt", optString("timestamp", "")),
    pinned = optBoolean("pinned", false),
)

internal fun JSONObject.toCronJob(): HermesCronJob = HermesCronJob(
    cronId = optString("cronId", optString("id", "cron-unknown")),
    name = optString("name", "Unnamed job"),
    schedule = optString("schedule", optString("expression", "")),
    enabled = optBoolean("enabled", true),
    description = optString("description", ""),
    nextRunAt = optString("nextRunAt", "").takeIf { it.isNotBlank() },
    lastRunAt = optString("lastRunAt", "").takeIf { it.isNotBlank() },
    lastStatus = optString("lastStatus", "").takeIf { it.isNotBlank() },
)

internal fun JSONObject.toLogEntry(): HermesLogEntry = HermesLogEntry(
    logId = optString("logId", optString("id", "log-unknown")),
    severity = optString("severity", optString("level", "info")),
    timestamp = optString("timestamp", ""),
    message = optString("message", optString("body", "")),
    source = optString("source", "").takeIf { it.isNotBlank() },
    sessionId = optString("sessionId", "").takeIf { it.isNotBlank() },
)

/**
 * Parses a list-or-unavailable response body. When the body is an
 * `UnavailablePayload`, returns a capability listing with empty items and the
 * unavailable signal. When the body is a bare array or a `{ items: [...] }`
 * envelope, returns the parsed list.
 */
internal fun <T> parseCapabilityListing(
    body: String,
    mapObject: (JSONObject) -> T,
): HermesCapabilityListing<T> {
    if (body.isBlank()) return HermesCapabilityListing(items = emptyList())
    val token = runCatching { JSONTokener(body).nextValue() }.getOrNull()
    return when (token) {
        is JSONArray -> {
            val items = buildList<T> { for (i in 0 until token.length()) token.optJSONObject(i)?.let { add(mapObject(it)) } }
            HermesCapabilityListing(items = items)
        }
        is JSONObject -> {
            val unavailable = token.toUnavailableOrNull()
            if (unavailable != null) {
                HermesCapabilityListing(items = emptyList(), unavailable = unavailable)
            } else {
                val array = token.optJSONArray("items")
                    ?: token.optJSONArray("data")
                    ?: token.optJSONArray("results")
                    ?: JSONArray().put(token)
                val items = buildList<T> { for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(mapObject(it)) } }
                HermesCapabilityListing(items = items)
            }
        }
        else -> HermesCapabilityListing(items = emptyList())
    }
}

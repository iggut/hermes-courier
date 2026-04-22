package com.hermescourier.android.domain.model

data class HermesDeviceIdentity(
    val deviceId: String,
    val platform: String,
    val appVersion: String,
    val publicKeyFingerprint: String,
)

data class HermesAuthChallengeRequest(
    val device: HermesDeviceIdentity,
    val nonce: String,
)

data class HermesAuthChallengeResponse(
    val challengeId: String,
    val nonce: String,
    val expiresAt: String,
    val trustLevel: String,
)

data class HermesAuthResponseRequest(
    val challengeId: String,
    val signedNonce: String,
    val device: HermesDeviceIdentity,
)

data class HermesAuthSession(
    val sessionId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val gatewayUrl: String,
    val mtlsRequired: Boolean,
    val scope: List<String>,
)

data class HermesDashboardSnapshot(
    val activeSessionCount: Int,
    val pendingApprovalCount: Int,
    val lastSyncLabel: String,
    val connectionState: String,
)

data class HermesSessionSummary(
    val sessionId: String,
    val title: String,
    val status: String,
    val updatedAt: String,
)

data class HermesApprovalSummary(
    val approvalId: String,
    val title: String,
    val detail: String,
    val requiresBiometrics: Boolean,
)

data class HermesConversationEvent(
    val eventId: String,
    val author: String,
    val body: String,
    val timestamp: String,
)

data class HermesConversationSendRequest(
    val body: String,
)


data class HermesApprovalActionResult(
    val approvalId: String,
    val action: String,
    val status: String,
    val detail: String,
    val updatedAt: String,
)

data class HermesSessionControlActionResult(
    val sessionId: String,
    val action: String,
    val status: String,
    val detail: String,
    val updatedAt: String,
    val endpoint: String? = null,
    val supported: Boolean = true,
)

data class HermesEndpointVerificationResult(
    val endpoint: String,
    val status: String,
    val reason: String,
)

/**
 * Shared representation of the `UnavailablePayload` contract shape. A capability
 * is unavailable when the gateway signals `supported: false`; clients MUST NOT
 * retry (regardless of `retryable`).
 */
data class HermesUnavailablePayload(
    val type: String,
    val detail: String,
    val endpoint: String? = null,
    val fallbackPollEndpoints: List<String> = emptyList(),
)

/**
 * Read-only listing of a feature. `items` is null when the gateway declared the
 * feature unsupported; `unavailable` carries the payload in that case.
 */
data class HermesCapabilityListing<T>(
    val items: List<T>,
    val unavailable: HermesUnavailablePayload? = null,
    val isSupported: Boolean = unavailable == null,
)

data class HermesSkill(
    val skillId: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val version: String? = null,
    val lastUsedAt: String? = null,
    val scopes: List<String> = emptyList(),
)

data class HermesMemoryItem(
    val memoryId: String,
    val title: String,
    val snippet: String = "",
    val body: String? = null,
    val tags: List<String> = emptyList(),
    val updatedAt: String = "",
    val pinned: Boolean = false,
)

data class HermesCronJob(
    val cronId: String,
    val name: String,
    val schedule: String,
    val enabled: Boolean = true,
    val description: String = "",
    val nextRunAt: String? = null,
    val lastRunAt: String? = null,
    val lastStatus: String? = null,
)

data class HermesLogEntry(
    val logId: String,
    val severity: String,
    val timestamp: String,
    val message: String,
    val source: String? = null,
    val sessionId: String? = null,
)

data class HermesRealtimeEnvelope(
    val type: String,
    val dashboard: HermesDashboardSnapshot? = null,
    val sessions: List<HermesSessionSummary>? = null,
    val approvals: List<HermesApprovalSummary>? = null,
    val conversation: HermesConversationEvent? = null,
    val approvalResult: HermesApprovalActionResult? = null,
    val sessionControlResult: HermesSessionControlActionResult? = null,
    val eventId: String? = null,
    val eventTimestamp: String? = null,
)

data class HermesGatewaySettings(
    val baseUrl: String = "https://gateway.hermes.local",
    val certificatePath: String = "",
    val certificatePassword: String = "",
)

data class HermesEnrollmentPayload(
    val gatewayUrl: String,
    val deviceId: String,
    val publicKeyFingerprint: String,
    val appVersion: String,
    val issuedAt: String,
    val courierMode: String? = null,
    val bearerToken: String? = null,
    val pairingMode: String? = null,
    val pairingContractVersion: String? = null,
    val apiBasePath: String? = null,
)

fun parseHermesEnrollmentPayload(
    payload: String,
): HermesEnrollmentPayload? {
    val uri = runCatching { java.net.URI(payload) }.getOrNull() ?: return null
    if (uri.scheme != "hermes-courier-enroll") return null
    val query = parseQueryParameters(uri.rawQuery)
    val gatewayUrl = query["gatewayUrl"]?.trim().orEmpty()
    if (gatewayUrl.isBlank()) return null
    val courierMode = query["courierMode"]?.trim()?.takeIf { it.isNotBlank() }
    val pairingMode = query["pairingMode"]?.trim()?.takeIf { it.isNotBlank() }
    val bearerFromQuery = query["bearerToken"]?.trim()?.takeIf { it.isNotBlank() }
        ?: query["token"]?.trim()?.takeIf { it.isNotBlank() }
    val isWebUiTokenOnly = courierMode?.equals("bearer-token", ignoreCase = true) == true &&
        pairingMode?.equals("token-only", ignoreCase = true) == true &&
        !bearerFromQuery.isNullOrBlank()

    var deviceId = query["deviceId"]?.trim().orEmpty()
    var publicKeyFingerprint = query["publicKeyFingerprint"]?.trim().orEmpty()
    var appVersion = query["appVersion"]?.trim().orEmpty()
    var issuedAt = query["issuedAt"]?.trim().orEmpty()
    if (isWebUiTokenOnly) {
        if (deviceId.isBlank()) deviceId = "webui-token-only"
        if (publicKeyFingerprint.isBlank()) publicKeyFingerprint = "not-applicable"
        if (appVersion.isBlank()) appVersion = "0.0.0"
        if (issuedAt.isBlank()) issuedAt = java.time.Instant.now().toString()
    } else if (deviceId.isBlank() || publicKeyFingerprint.isBlank() || appVersion.isBlank() || issuedAt.isBlank()) {
        return null
    }
    val pairingContractVersion = query["pairingContractVersion"]?.trim()?.takeIf { it.isNotBlank() }
    val apiBasePath = query["apiBasePath"]?.trim()?.takeIf { it.isNotBlank() }
    return HermesEnrollmentPayload(
        gatewayUrl = gatewayUrl,
        deviceId = deviceId,
        publicKeyFingerprint = publicKeyFingerprint,
        appVersion = appVersion,
        issuedAt = issuedAt,
        courierMode = courierMode,
        bearerToken = bearerFromQuery,
        pairingMode = pairingMode,
        pairingContractVersion = pairingContractVersion,
        apiBasePath = apiBasePath,
    )
}

fun validateTokenOnlyPairingContract(payload: HermesEnrollmentPayload): String? {
    if (payload.courierMode?.lowercase() != "bearer-token") {
        return "Pairing import failed: courierMode must be bearer-token"
    }
    if (payload.pairingMode?.lowercase() != "token-only") {
        return "Pairing import failed: pairingMode must be token-only"
    }
    if (payload.gatewayUrl.isBlank()) {
        return "Pairing import failed: gatewayUrl is required"
    }
    if (payload.bearerToken.isNullOrBlank()) {
        return "Pairing import failed: token-only pairing requires bearerToken"
    }
    return null
}

private fun parseQueryParameters(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery.split("&")
        .mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val pieces = part.split("=", limit = 2)
            val key = java.net.URLDecoder.decode(pieces[0], Charsets.UTF_8.name())
            val value = java.net.URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8.name())
            key to value
        }
        .toMap()
}

data class HermesQueuedApprovalAction(
    val approvalId: String,
    val action: String,
    val note: String?,
    val createdAt: Long,
)

/** UI label: wire uses `deny` while surfaces say "Reject". */
fun userFacingApprovalVerb(action: String): String = when (action.trim().lowercase()) {
    "deny", "reject" -> "Reject"
    "approve" -> "Approve"
    else -> action.replaceFirstChar { it.uppercaseChar() }
}

/**
 * Body `decision` for `POST /v1/approvals/{id}/decision`: lowercases and maps legacy `reject` to `deny`.
 */
fun normalizeApprovalDecisionWire(raw: String): String = when (raw.lowercase()) {
    "reject" -> "deny"
    else -> raw.lowercase()
}

/**
 * Returns true when a queued approval action has been acknowledged by a live approval result.
 * We intentionally match by approval ID and normalized action so legacy `reject`/`deny`
 * variants reconcile cleanly when the live stream reports the server-side decision.
 */
fun queuedApprovalActionMatchesResult(
    queued: HermesQueuedApprovalAction,
    result: HermesApprovalActionResult,
): Boolean = queued.approvalId == result.approvalId &&
    normalizeApprovalDecisionWire(queued.action) == normalizeApprovalDecisionWire(result.action)

/**
 * When loading persisted queued actions, map legacy `reject` to wire `deny` without lowercasing other values.
 */
fun migrateQueuedApprovalAction(action: String): String =
    if (action.equals("reject", ignoreCase = true)) "deny" else action

enum class HermesConversationActionState {
    Idle,
    Sending,
    Sent,
    Failed,
}

data class HermesCourierUiState(
    val bootstrapState: String = "Bootstrapping secure gateway",
    val authStatus: String = "Waiting for device-bound challenge",
    val gatewayConnectionMode: String = "Unknown",
    val gatewayConnectionDetail: String = "No gateway check has run yet",
    val dashboard: HermesDashboardSnapshot = HermesDashboardSnapshot(
        activeSessionCount = 0,
        pendingApprovalCount = 0,
        lastSyncLabel = "Never",
        connectionState = "Disconnected",
    ),
    val sessions: List<HermesSessionSummary> = emptyList(),
    val approvals: List<HermesApprovalSummary> = emptyList(),
    val conversationEvents: List<HermesConversationEvent> = listOf(
        HermesConversationEvent(
            eventId = "boot-1",
            author = "Hermes",
            body = "Awaiting secure gateway bootstrap.",
            timestamp = "now",
        )
    ),
    val conversationActionStatus: String = "No instruction sent yet",
    val conversationActionError: String? = null,
    val conversationActionState: HermesConversationActionState = HermesConversationActionState.Idle,
    val approvalActionStatus: String = "No approval action submitted",
    val streamStatus: String = "Realtime stream disconnected",
    val realtimeReconnectCountdown: String = "Reconnect now",
    val realtimeReconnectProgress: Float = 0f,
    val gatewaySettings: HermesGatewaySettings = HermesGatewaySettings(),
    val deviceFingerprint: String = "pending-device-enrollment",
    val enrollmentStatus: String = "No certificate imported yet",
    val courierPairingStatus: String = "No paired bearer token configured",
    val enrollmentQrPayload: String = "",
    val queuedApprovalActions: Int = 0,
    val queuedApprovalActionQueue: List<HermesQueuedApprovalAction> = emptyList(),
    val endpointVerificationResults: List<HermesEndpointVerificationResult> = emptyList(),
    val verificationMode: String = "Not run",
    val pairingBackendStatus: String = "Pairing backend status not checked yet",
    val pairingBackendDetail: String = "Status endpoint has not been queried",
    val pairingUnavailableReasons: List<String> = emptyList(),
    val sessionControlStatus: String = "No session-control action submitted",
    val sessionDetailLoading: Boolean = false,
    val sessionDetailLoadError: String? = null,
    val skills: HermesCapabilityListing<HermesSkill> = HermesCapabilityListing(items = emptyList()),
    val memory: HermesCapabilityListing<HermesMemoryItem> = HermesCapabilityListing(items = emptyList()),
    val cronJobs: HermesCapabilityListing<HermesCronJob> = HermesCapabilityListing(items = emptyList()),
    val logs: HermesCapabilityListing<HermesLogEntry> = HermesCapabilityListing(items = emptyList()),
    val libraryStatus: String = "Not loaded yet",
    val libraryLoading: Boolean = false,
    /**
     * Operator-selected "active" Hermes work session surfaced in the Chat screen.
     *
     * Client-side concept only: the backend conversation endpoint is currently global
     * (`/v1/conversation`) and does not accept a `sessionId`, so this value drives UI
     * context (header chip, entry points from Sessions/SessionDetail) without filtering
     * the message stream. When null, Chat shows the global conversation without a
     * session context chip.
     */
    val activeSessionId: String? = null,
)

package com.hermescourier.android.domain.gateway

import com.hermescourier.android.domain.auth.HermesChallengeSigner
import com.hermescourier.android.domain.config.HermesGatewayConfiguration
import com.hermescourier.android.domain.model.HermesApprovalActionResult
import com.hermescourier.android.domain.model.normalizeApprovalDecisionWire
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesAuthChallengeRequest
import com.hermescourier.android.domain.model.HermesAuthChallengeResponse
import com.hermescourier.android.domain.model.HermesAuthResponseRequest
import com.hermescourier.android.domain.model.HermesAuthSession
import com.hermescourier.android.domain.model.HermesConversationEvent
import com.hermescourier.android.domain.model.HermesConversationSendRequest
import com.hermescourier.android.domain.model.HermesDashboardSnapshot
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import com.hermescourier.android.domain.model.HermesEndpointVerificationResult
import com.hermescourier.android.domain.model.HermesRealtimeEnvelope
import com.hermescourier.android.domain.model.HermesSessionControlActionResult
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.domain.storage.HermesTokenStore
import com.hermescourier.android.domain.transport.HermesGatewayTransport
import java.io.Closeable
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

interface HermesGatewayClient {
    suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession
    suspend fun fetchDashboard(session: HermesAuthSession): HermesDashboardSnapshot
    suspend fun fetchSessions(session: HermesAuthSession): List<HermesSessionSummary>
    suspend fun fetchSessionDetail(session: HermesAuthSession, sessionId: String): HermesSessionSummary
    suspend fun submitSessionControlAction(
        session: HermesAuthSession,
        sessionId: String,
        action: String,
    ): HermesSessionControlActionResult
    suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary>
    suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent>
    suspend fun submitConversationMessage(session: HermesAuthSession, message: String): HermesConversationEvent?
    suspend fun submitApprovalAction(
        session: HermesAuthSession,
        approvalId: String,
        action: String,
        note: String? = null,
    ): HermesApprovalActionResult

    fun connectRealtime(
        session: HermesAuthSession,
        onStatus: (String) -> Unit,
        onEnvelope: (HermesRealtimeEnvelope) -> Unit,
    ): Closeable

    suspend fun verifyLiveEndpoints(device: HermesDeviceIdentity): List<HermesEndpointVerificationResult>
}

class NetworkHermesGatewayClient(
    private val transport: HermesGatewayTransport,
    private val tokenStore: HermesTokenStore,
    private val signer: HermesChallengeSigner,
    private val okHttpClient: OkHttpClient,
    private val configuration: HermesGatewayConfiguration,
) : HermesGatewayClient {
    override suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession = withContext(Dispatchers.IO) {
        tokenStore.load()?.let { paired ->
            if (paired.accessToken.isNotBlank()) {
                val storedBase = paired.gatewayUrl.trim().toHttpUrlOrNull()
                if (storedBase != null && pairedGatewayBaseMatches(storedBase, configuration.baseUrl)) {
                    return@withContext paired
                }
            }
        }
        val challenge = requestChallenge(device)
        val signedNonce = signer.sign(challenge.nonce, device)
        val response = transport.post(
            path = "/${HermesApiPaths.AUTH_RESPONSE}",
            body = HermesAuthResponseRequest(
                challengeId = challenge.challengeId,
                signedNonce = signedNonce,
                device = device,
            ).toJson(),
        )
        val session = response.toJsonObject().toSession()
        tokenStore.save(session)
        session
    }

    override suspend fun fetchDashboard(session: HermesAuthSession): HermesDashboardSnapshot = withContext(Dispatchers.IO) {
        transport.get("/${HermesApiPaths.DASHBOARD}", session.accessToken).toJsonObject().toDashboard()
    }

    override suspend fun fetchSessions(session: HermesAuthSession): List<HermesSessionSummary> = withContext(Dispatchers.IO) {
        transport.get("/${HermesApiPaths.SESSIONS}", session.accessToken).toJsonArrayOrObject().toSessionList()
    }

    override suspend fun fetchSessionDetail(session: HermesAuthSession, sessionId: String): HermesSessionSummary = withContext(Dispatchers.IO) {
        transport.get("/${HermesApiPaths.sessionDetail(sessionId)}", session.accessToken).toJsonObject().toSessionSummary()
    }

    override suspend fun submitSessionControlAction(
        session: HermesAuthSession,
        sessionId: String,
        action: String,
    ): HermesSessionControlActionResult = withContext(Dispatchers.IO) {
        val normalizedAction = normalizeSessionControlActionWire(action)
        val candidates = HermesApiPaths.sessionControlCandidates(sessionId, normalizedAction)
        var unsupportedCount = 0
        var lastFailure = "No session-control endpoint candidates were configured."
        for (candidate in candidates) {
            val payload = candidate.bodyFactory(normalizedAction)
            runCatching {
                transport.post(candidate.path, payload, session.accessToken)
            }.onSuccess { raw ->
                val fallback = HermesSessionControlActionResult(
                    sessionId = sessionId,
                    action = normalizedAction,
                    status = "submitted",
                    detail = "Session-control action accepted.",
                    updatedAt = "now",
                    endpoint = candidate.path,
                )
                return@withContext if (raw.isBlank()) {
                    fallback
                } else {
                    raw.toJsonObject().toSessionControlActionResult(fallback)
                }
            }.onFailure { error ->
                val message = error.message ?: error.toString()
                lastFailure = "POST ${candidate.path} failed: $message"
                if (message.contains(" 404:") || message.contains(" 405:")) {
                    unsupportedCount += 1
                } else {
                    return@withContext HermesSessionControlActionResult(
                        sessionId = sessionId,
                        action = normalizedAction,
                        status = "failed",
                        detail = lastFailure,
                        updatedAt = "now",
                        endpoint = candidate.path,
                    )
                }
            }
        }
        if (unsupportedCount == candidates.size) {
            return@withContext HermesSessionControlActionResult(
                sessionId = sessionId,
                action = normalizedAction,
                status = "unsupported",
                detail = "Gateway does not expose a supported session-control endpoint for action '$normalizedAction'.",
                updatedAt = "now",
                supported = false,
            )
        }
        HermesSessionControlActionResult(
            sessionId = sessionId,
            action = normalizedAction,
            status = "failed",
            detail = lastFailure,
            updatedAt = "now",
        )
    }

    override suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary> = withContext(Dispatchers.IO) {
        transport.get("/${HermesApiPaths.APPROVALS}", session.accessToken).toJsonArrayOrObject().toApprovalList()
    }

    override suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent> = withContext(Dispatchers.IO) {
        transport.get("/${HermesApiPaths.CONVERSATION}", session.accessToken).toJsonArrayOrObject().toConversationList()
    }

    override suspend fun submitConversationMessage(session: HermesAuthSession, message: String): HermesConversationEvent? = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("body", message.trim())
        val response = transport.post("/${HermesApiPaths.CONVERSATION}", payload, session.accessToken)
        response.toConversationEventOrNull(message.trim())
    }

    override suspend fun submitApprovalAction(
        session: HermesAuthSession,
        approvalId: String,
        action: String,
        note: String?,
    ): HermesApprovalActionResult = withContext(Dispatchers.IO) {
        val decision = normalizeApprovalDecisionWire(action)
        val body = JSONObject().put("decision", decision)
        if (!note.isNullOrBlank()) {
            body.put("reason", note)
        }
        val result = transport.post(
            path = "/${HermesApiPaths.approvalDecision(approvalId)}",
            bearerToken = session.accessToken,
            body = body,
        )
        if (result.isBlank()) {
            return@withContext HermesApprovalActionResult(
                approvalId = approvalId,
                action = decision,
                status = "recorded",
                detail = note ?: "Decision recorded.",
                updatedAt = "now",
            )
        }
        result.toJsonObject().toApprovalActionResult(approvalId, decision)
    }

    override fun connectRealtime(
        session: HermesAuthSession,
        onStatus: (String) -> Unit,
        onEnvelope: (HermesRealtimeEnvelope) -> Unit,
    ): Closeable {
        val manager = RealtimeConnectionManager(
            okHttpClient = okHttpClient,
            configuration = configuration,
            session = session,
            onStatus = onStatus,
            onEnvelope = onEnvelope,
        )
        manager.start()
        return manager
    }

    override suspend fun verifyLiveEndpoints(device: HermesDeviceIdentity): List<HermesEndpointVerificationResult> = withContext(Dispatchers.IO) {
        val checks = mutableListOf<HermesEndpointVerificationResult>()
        val boot = try {
            Result.success(bootstrap(device))
        } catch (e: Throwable) {
            Result.failure(e)
        }
        if (boot.isFailure) {
            val reason = boot.exceptionOrNull()?.message ?: boot.exceptionOrNull().toString()
            checks += HermesEndpointVerificationResult("auth/bootstrap", "failed", reason)
            checks += HermesEndpointVerificationResult("dashboard", "skipped", "Skipped because auth/bootstrap failed.")
            checks += HermesEndpointVerificationResult("sessions list", "skipped", "Skipped because auth/bootstrap failed.")
            checks += HermesEndpointVerificationResult("session detail", "skipped", "Skipped because auth/bootstrap failed.")
            checks += HermesEndpointVerificationResult("session-control pause", "skipped", "Skipped because auth/bootstrap failed.")
            checks += HermesEndpointVerificationResult("session-control resume", "skipped", "Skipped because auth/bootstrap failed.")
            checks += HermesEndpointVerificationResult("session-control terminate", "skipped", "Skipped because auth/bootstrap failed.")
            checks += HermesEndpointVerificationResult("approvals", "skipped", "Skipped because auth/bootstrap failed.")
            checks += HermesEndpointVerificationResult("conversation list", "skipped", "Skipped because auth/bootstrap failed.")
            checks += HermesEndpointVerificationResult("conversation send", "skipped", "Skipped because auth/bootstrap failed.")
            checks += HermesEndpointVerificationResult("realtime/events", "skipped", "Skipped because auth/bootstrap failed.")
            return@withContext checks
        }
        checks += HermesEndpointVerificationResult("auth/bootstrap", "ok", "Challenge-response bootstrap succeeded.")
        val session = boot.getOrThrow()

        checks += runCheck("dashboard") { fetchDashboard(session) }
        val sessionsOutcome = try {
            Result.success(fetchSessions(session))
        } catch (e: Throwable) {
            Result.failure(e)
        }
        if (sessionsOutcome.isSuccess) {
            checks += HermesEndpointVerificationResult("sessions list", "ok", "Request succeeded.")
            val sessions = sessionsOutcome.getOrThrow()
            if (sessions.isEmpty()) {
                checks += HermesEndpointVerificationResult("session detail", "skipped", "No session available to verify detail endpoint.")
                checks += HermesEndpointVerificationResult("session-control pause", "skipped", "No session available to verify control endpoint.")
                checks += HermesEndpointVerificationResult("session-control resume", "skipped", "No session available to verify control endpoint.")
                checks += HermesEndpointVerificationResult("session-control terminate", "skipped", "No session available to verify control endpoint.")
            } else {
                val firstSession = sessions.first()
                checks += runCheck("session detail") { fetchSessionDetail(session, firstSession.sessionId) }
                checks += toSessionControlVerificationResult("session-control pause", submitSessionControlAction(session, firstSession.sessionId, "pause"))
                checks += toSessionControlVerificationResult("session-control resume", submitSessionControlAction(session, firstSession.sessionId, "resume"))
                checks += toSessionControlVerificationResult("session-control terminate", submitSessionControlAction(session, firstSession.sessionId, "terminate"))
            }
        } else {
            checks += HermesEndpointVerificationResult(
                "sessions list",
                "failed",
                sessionsOutcome.exceptionOrNull()?.message ?: sessionsOutcome.exceptionOrNull().toString(),
            )
            checks += HermesEndpointVerificationResult("session detail", "skipped", "Skipped because sessions list failed.")
            checks += HermesEndpointVerificationResult("session-control pause", "skipped", "Skipped because sessions list failed.")
            checks += HermesEndpointVerificationResult("session-control resume", "skipped", "Skipped because sessions list failed.")
            checks += HermesEndpointVerificationResult("session-control terminate", "skipped", "Skipped because sessions list failed.")
        }
        checks += runCheck("approvals") { fetchApprovals(session) }
        checks += runCheck("conversation list") { fetchConversation(session) }
        checks += runCheck("conversation send") {
            submitConversationMessage(session, "live verification ping")
        }
        checks += runCheck("realtime/events") {
            val handle = connectRealtime(session, onStatus = {}, onEnvelope = {})
            handle.close()
        }
        checks
    }

    private suspend fun requestChallenge(device: HermesDeviceIdentity): HermesAuthChallengeResponse = withContext(Dispatchers.IO) {
        val response = transport.post(
            path = "/${HermesApiPaths.AUTH_CHALLENGE}",
            body = HermesAuthChallengeRequest(device = device, nonce = generateNonce()).toJson(),
        )
        response.toJsonObject().toChallenge()
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/**
 * Token-only pairing persists [HermesAuthSession.gatewayUrl] as the base captured at import time;
 * [HermesGatewayConfiguration.baseUrl] can differ by trivial normalization (for example a trailing
 * slash). Courier-enabled gateways reject unauthenticated [HermesApiPaths.AUTH_CHALLENGE] calls,
 * so a strict [HttpUrl] mismatch must not force a challenge round-trip.
 */
private fun pairedGatewayBaseMatches(stored: HttpUrl, configured: HttpUrl): Boolean {
    if (stored == configured) return true
    return stored.scheme == configured.scheme &&
        stored.host == configured.host &&
        stored.port == configured.port
}

private class RealtimeConnectionManager(
    private val okHttpClient: OkHttpClient,
    private val configuration: HermesGatewayConfiguration,
    private val session: HermesAuthSession,
    private val onStatus: (String) -> Unit,
    private val onEnvelope: (HermesRealtimeEnvelope) -> Unit,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var currentSocket: WebSocket? = null
    @Volatile
    private var cancelled = false

    fun start() {
        scope.launch {
            var attempt = 0
            while (isActive && !cancelled) {
                val completed = CompletableDeferred<Int>()
                val request = Request.Builder()
                    .url(configuration.baseUrl.newBuilder().addPathSegments(HermesApiPaths.EVENTS_STREAM).build())
                    .addHeader("Authorization", "Bearer ${session.accessToken}")
                    .build()
                onStatus(if (attempt == 0) "Realtime stream connecting" else "Realtime stream reconnecting")
                currentSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        attempt = 0
                        onStatus("Realtime stream connected")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching { text.toJsonObject().toRealtimeEnvelope() }
                            .onSuccess(onEnvelope)
                            .onFailure { onStatus("Realtime parse error: ${it.message ?: "unknown"}") }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        onStatus("Realtime stream error: ${t.message ?: "unknown"}")
                        completed.complete(-1)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        onStatus("Realtime stream closed ($code) $reason")
                        completed.complete(code)
                    }
                })
                val closeCode = runCatching { completed.await() }.getOrDefault(-1)
                if (!isActive || cancelled) {
                    break
                }
                if (closeCode == 1000) {
                    onStatus("Realtime stream closed cleanly; reconnecting")
                }
                attempt = (attempt + 1).coerceAtMost(5)
                val backoffSeconds = (1L shl attempt).coerceAtMost(30L)
                onStatus("Realtime reconnecting in ${backoffSeconds}s")
                delay(backoffSeconds * 1000)
            }
        }
    }

    override fun close() {
        cancelled = true
        currentSocket?.close(1000, "client disconnect")
        currentSocket = null
        scope.cancel()
    }
}

class DemoHermesGatewayClient : HermesGatewayClient {
    override suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession = HermesAuthSession(
        sessionId = "demo-session",
        accessToken = "demo-access-token",
        refreshToken = "demo-refresh-token",
        expiresAt = "2099-01-01T00:00:00Z",
        gatewayUrl = "https://demo.hermes.local",
        mtlsRequired = false,
        scope = listOf("dashboard:read", "sessions:read", "approvals:write", "events:read"),
    )

    override suspend fun fetchDashboard(session: HermesAuthSession): HermesDashboardSnapshot = HermesDashboardSnapshot(
        activeSessionCount = 2,
        pendingApprovalCount = 1,
        lastSyncLabel = "Just now",
        connectionState = "Demo fallback connected",
    )

    override suspend fun fetchSessions(session: HermesAuthSession): List<HermesSessionSummary> = listOf(
        HermesSessionSummary("demo-1", "Morning planning", "Running", "2m ago"),
        HermesSessionSummary("demo-2", "Tooling review", "Paused", "15m ago"),
    )

    override suspend fun fetchSessionDetail(session: HermesAuthSession, sessionId: String): HermesSessionSummary =
        fetchSessions(session).firstOrNull { it.sessionId == sessionId }
            ?: HermesSessionSummary(sessionId, "Demo session", "Unknown", "now")

    override suspend fun submitSessionControlAction(
        session: HermesAuthSession,
        sessionId: String,
        action: String,
    ): HermesSessionControlActionResult = HermesSessionControlActionResult(
        sessionId = sessionId,
        action = normalizeSessionControlActionWire(action),
        status = "demo-complete",
        detail = "Demo fallback accepted the session-control action.",
        updatedAt = "just now",
        endpoint = "demo",
    )

    override suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary> = listOf(
        HermesApprovalSummary("approval-1", "Deploy branch", "Approve production deployment for feature work.", true),
        HermesApprovalSummary("approval-2", "Share transcript", "Allow sharing the latest conversation transcript.", false),
    )

    override suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent> = listOf(
        HermesConversationEvent("event-01", "Hermes", "Awaiting your next instruction.", "now"),
        HermesConversationEvent("event-02", "You", "Review the latest approvals.", "just now"),
        HermesConversationEvent("event-03", "Hermes", "I found 2 pending approval requests.", "just now"),
    )

    override suspend fun submitConversationMessage(session: HermesAuthSession, message: String): HermesConversationEvent? = HermesConversationEvent(
        eventId = "demo-${System.currentTimeMillis()}",
        author = "You",
        body = message.trim(),
        timestamp = "now",
    )

    override suspend fun submitApprovalAction(
        session: HermesAuthSession,
        approvalId: String,
        action: String,
        note: String?,
    ): HermesApprovalActionResult = HermesApprovalActionResult(
        approvalId = approvalId,
        action = action,
        status = "demo-complete",
        detail = note ?: "Demo fallback accepted the approval action.",
        updatedAt = "just now",
    )

    override fun connectRealtime(
        session: HermesAuthSession,
        onStatus: (String) -> Unit,
        onEnvelope: (HermesRealtimeEnvelope) -> Unit,
    ): Closeable {
        onStatus("Realtime stream connected (demo)")
        onEnvelope(
            HermesRealtimeEnvelope(
                type = "conversation",
                conversation = HermesConversationEvent(
                    eventId = "demo-stream-1",
                    author = "Hermes",
                    body = "Demo realtime stream active.",
                    timestamp = "now",
                ),
            ),
        )
        return Closeable { onStatus("Realtime stream closed (demo)") }
    }

    override suspend fun verifyLiveEndpoints(device: HermesDeviceIdentity): List<HermesEndpointVerificationResult> = listOf(
        HermesEndpointVerificationResult("auth/bootstrap", "demo", "Demo fallback"),
        HermesEndpointVerificationResult("dashboard", "demo", "Demo fallback"),
        HermesEndpointVerificationResult("sessions list/detail", "demo", "Demo fallback"),
        HermesEndpointVerificationResult("session-control actions", "demo", "Demo fallback"),
        HermesEndpointVerificationResult("approvals", "demo", "Demo fallback"),
        HermesEndpointVerificationResult("conversation", "demo", "Demo fallback"),
        HermesEndpointVerificationResult("realtime/events", "demo", "Demo fallback"),
    )
}

private fun HermesAuthChallengeResponse.toJson(): JSONObject = JSONObject()

private fun HermesAuthChallengeRequest.toJson(): JSONObject = JSONObject()
    .put("device", device.toJson())
    .put("nonce", nonce)

private fun HermesAuthResponseRequest.toJson(): JSONObject = JSONObject()
    .put("challengeId", challengeId)
    .put("signedNonce", signedNonce)
    .put("device", device.toJson())

private fun HermesDeviceIdentity.toJson(): JSONObject = JSONObject()
    .put("deviceId", deviceId)
    .put("platform", platform)
    .put("appVersion", appVersion)
    .put("publicKeyFingerprint", publicKeyFingerprint)

private fun normalizeSessionControlActionWire(raw: String): String = when (raw.trim().lowercase()) {
    "stop" -> "terminate"
    else -> raw.trim().lowercase()
}

private data class SessionControlCandidate(
    val path: String,
    val bodyFactory: (String) -> JSONObject,
)

private fun HermesApiPaths.sessionControlCandidates(sessionId: String, action: String): List<SessionControlCandidate> = listOf(
    SessionControlCandidate(
        path = "/${HermesApiPaths.sessionControlAction(sessionId)}",
        bodyFactory = { normalized -> JSONObject().put("action", normalized) },
    ),
    SessionControlCandidate(
        path = "/${HermesApiPaths.sessionActionEndpoint(sessionId, action)}",
        bodyFactory = { _ -> JSONObject() },
    ),
)

private suspend fun <T> runCheck(
    endpoint: String,
    call: suspend () -> T,
): HermesEndpointVerificationResult = try {
    call()
    HermesEndpointVerificationResult(endpoint, "ok", "Request succeeded.")
} catch (e: Throwable) {
    HermesEndpointVerificationResult(endpoint, "failed", e.message ?: e.toString())
}

private fun toSessionControlVerificationResult(
    endpoint: String,
    result: HermesSessionControlActionResult,
): HermesEndpointVerificationResult = HermesEndpointVerificationResult(
    endpoint = endpoint,
    status = if (result.supported && result.status != "failed") "ok" else if (!result.supported) "unsupported" else "failed",
    reason = result.detail + (result.endpoint?.let { " (endpoint: $it)" } ?: ""),
)

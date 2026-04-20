package com.hermescourier.android.domain.gateway

import com.hermescourier.android.domain.auth.HermesChallengeSigner
import com.hermescourier.android.domain.config.HermesGatewayConfiguration
import com.hermescourier.android.domain.model.HermesApprovalActionResult
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesAuthChallengeRequest
import com.hermescourier.android.domain.model.HermesAuthChallengeResponse
import com.hermescourier.android.domain.model.HermesAuthResponseRequest
import com.hermescourier.android.domain.model.HermesAuthSession
import com.hermescourier.android.domain.model.HermesConversationEvent
import com.hermescourier.android.domain.model.HermesDashboardSnapshot
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import com.hermescourier.android.domain.model.HermesRealtimeEnvelope
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

interface HermesGatewayClient {
    suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession
    suspend fun fetchDashboard(session: HermesAuthSession): HermesDashboardSnapshot
    suspend fun fetchSessions(session: HermesAuthSession): List<HermesSessionSummary>
    suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary>
    suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent>
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
}

class NetworkHermesGatewayClient(
    private val transport: HermesGatewayTransport,
    private val tokenStore: HermesTokenStore,
    private val signer: HermesChallengeSigner,
    private val okHttpClient: OkHttpClient,
    private val configuration: HermesGatewayConfiguration,
) : HermesGatewayClient {
    override suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession = withContext(Dispatchers.IO) {
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

    override suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary> = withContext(Dispatchers.IO) {
        transport.get("/${HermesApiPaths.APPROVALS}", session.accessToken).toJsonArrayOrObject().toApprovalList()
    }

    override suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent> = withContext(Dispatchers.IO) {
        transport.get("/${HermesApiPaths.CONVERSATION}", session.accessToken).toJsonArrayOrObject().toConversationList()
    }

    override suspend fun submitApprovalAction(
        session: HermesAuthSession,
        approvalId: String,
        action: String,
        note: String?,
    ): HermesApprovalActionResult = withContext(Dispatchers.IO) {
        val decision = normalizeApprovalDecision(action)
        val body = JSONObject()
            .put("decision", decision)
            .put("reason", note)
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

    override suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary> = listOf(
        HermesApprovalSummary("approval-1", "Deploy branch", "Approve production deployment for feature work.", true),
        HermesApprovalSummary("approval-2", "Share transcript", "Allow sharing the latest conversation transcript.", false),
    )

    override suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent> = listOf(
        HermesConversationEvent("event-01", "Hermes", "Awaiting your next instruction.", "now"),
        HermesConversationEvent("event-02", "You", "Review the latest approvals.", "just now"),
        HermesConversationEvent("event-03", "Hermes", "I found 2 pending approval requests.", "just now"),
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
}

private fun HermesAuthChallengeResponse.toJson(): JSONObject = JSONObject()

private fun HermesAuthChallengeRequest.toJson(): JSONObject = JSONObject()
    .put("device", device.toJson())
    .put("nonce", nonce)

private fun HermesAuthResponseRequest.toJson(): JSONObject = JSONObject()
    .put("challengeId", challengeId)
    .put("signedNonce", signedNonce)
    .put("device", device.toJson())

private fun normalizeApprovalDecision(raw: String): String = when (raw.lowercase()) {
    "reject" -> "deny"
    else -> raw.lowercase()
}

private fun HermesDeviceIdentity.toJson(): JSONObject = JSONObject()
    .put("deviceId", deviceId)
    .put("platform", platform)
    .put("appVersion", appVersion)
    .put("publicKeyFingerprint", publicKeyFingerprint)

private fun String.toJsonObject(): JSONObject = JSONObject(this)

private fun String.toJsonArrayOrObject(): JSONArray {
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

private fun JSONObject.toChallenge(): HermesAuthChallengeResponse = HermesAuthChallengeResponse(
    challengeId = getString("challengeId"),
    nonce = getString("nonce"),
    expiresAt = optString("expiresAt", ""),
    trustLevel = optString("trustLevel", "unknown"),
)

private fun JSONObject.toSession(): HermesAuthSession = HermesAuthSession(
    sessionId = getString("sessionId"),
    accessToken = getString("accessToken"),
    refreshToken = getString("refreshToken"),
    expiresAt = getString("expiresAt"),
    gatewayUrl = getString("gatewayUrl"),
    mtlsRequired = optBoolean("mtlsRequired", true),
    scope = optJSONArray("scope")?.toStringList() ?: emptyList(),
)

private fun JSONObject.toDashboard(): HermesDashboardSnapshot = HermesDashboardSnapshot(
    activeSessionCount = optInt("activeSessionCount", 0),
    pendingApprovalCount = optInt("pendingApprovalCount", 0),
    lastSyncLabel = optString("lastSyncLabel", "Never"),
    connectionState = optString("connectionState", "Connected"),
)

private fun JSONObject.toSessionSummary(): HermesSessionSummary = HermesSessionSummary(
    sessionId = optString("sessionId", optString("id", "session-unknown")),
    title = optString("title", "Untitled session"),
    status = optString("status", "unknown"),
    updatedAt = optString("updatedAt", "unknown"),
)

private fun JSONObject.toApprovalSummary(): HermesApprovalSummary = HermesApprovalSummary(
    approvalId = optString("approvalId", optString("id", "approval-unknown")),
    title = optString("title", "Approval"),
    detail = optString("detail", ""),
    requiresBiometrics = optBoolean("requiresBiometrics", false),
)

private fun JSONObject.toConversationEvent(): HermesConversationEvent = HermesConversationEvent(
    eventId = optString("eventId", optString("id", "event-unknown")),
    author = optString("author", "Hermes"),
    body = optString("body", optString("message", "")),
    timestamp = optString("timestamp", "now"),
)

private fun JSONObject.toApprovalActionResult(
    fallbackApprovalId: String,
    fallbackDecision: String,
): HermesApprovalActionResult = HermesApprovalActionResult(
    approvalId = optString("approvalId", fallbackApprovalId),
    action = optString("action", optString("decision", fallbackDecision)),
    status = optString("status", "submitted"),
    detail = optString("detail", optString("message", "Approval action submitted.")),
    updatedAt = optString("updatedAt", "now"),
)

private fun JSONObject.toApprovalActionResult(): HermesApprovalActionResult = HermesApprovalActionResult(
    approvalId = optString("approvalId", "unknown"),
    action = optString("action", optString("decision", "unknown")),
    status = optString("status", "submitted"),
    detail = optString("detail", optString("message", "Approval action submitted.")),
    updatedAt = optString("updatedAt", "now"),
)

private fun JSONObject.toRealtimeEnvelope(): HermesRealtimeEnvelope {
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
    )
}

private fun JSONArray.toSessionList(): List<HermesSessionSummary> = mutableListOf<HermesSessionSummary>().apply {
    for (index in 0 until length()) add(getJSONObject(index).toSessionSummary())
}

private fun JSONArray.toApprovalList(): List<HermesApprovalSummary> = mutableListOf<HermesApprovalSummary>().apply {
    for (index in 0 until length()) add(getJSONObject(index).toApprovalSummary())
}

private fun JSONArray.toConversationList(): List<HermesConversationEvent> = mutableListOf<HermesConversationEvent>().apply {
    for (index in 0 until length()) add(getJSONObject(index).toConversationEvent())
}

private fun JSONArray.toStringList(): List<String> = mutableListOf<String>().apply {
    for (index in 0 until length()) add(getString(index))
}

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
import com.hermescourier.android.domain.model.HermesModel
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
import android.util.Log

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
    suspend fun fetchConversation(
        session: HermesAuthSession,
        sessionId: String?,
    ): List<HermesConversationEvent>
    suspend fun submitConversationMessage(
        session: HermesAuthSession,
        message: String,
        sessionId: String?,
        model: String?,
    ): HermesConversationEvent?
    suspend fun submitApprovalAction(
        session: HermesAuthSession,
        approvalId: String,
        action: String,
        note: String?,
    ): HermesApprovalActionResult

    fun connectRealtime(
        session: HermesAuthSession,
        onStatus: (String) -> Unit,
        onEnvelope: (HermesRealtimeEnvelope) -> Unit,
    ): Closeable

    suspend fun verifyLiveEndpoints(device: HermesDeviceIdentity): List<HermesEndpointVerificationResult>
    suspend fun fetchModels(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<HermesModel>
    suspend fun fetchSkills(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<com.hermescourier.android.domain.model.HermesSkill>
    suspend fun saveSkill(session: HermesAuthSession, name: String, content: String, category: String): Boolean
    suspend fun deleteSkill(session: HermesAuthSession, name: String): Boolean
    suspend fun fetchSkillContent(session: HermesAuthSession, name: String, category: String): String?
    suspend fun fetchMemory(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<com.hermescourier.android.domain.model.HermesMemoryItem>
    suspend fun fetchCronJobs(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<com.hermescourier.android.domain.model.HermesCronJob>
    suspend fun fetchLogs(session: HermesAuthSession, limit: Int?, severity: String?): com.hermescourier.android.domain.model.HermesCapabilityListing<com.hermescourier.android.domain.model.HermesLogEntry>
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
        if (configuration.mtlsPkcs12File != null) {
            val session = HermesAuthSession(
                sessionId = "mtls-${device.deviceId.takeLast(8)}",
                accessToken = "",
                refreshToken = "",
                expiresAt = "",
                gatewayUrl = configuration.baseUrl.toString(),
                mtlsRequired = true,
                scope = emptyList(),
            )
            tokenStore.save(session)
            return@withContext session
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
        Log.i("HermesSessionCtl", "submit sessionId=$sessionId action=$normalizedAction candidates=${candidates.joinToString { it.path }}")
        var unsupportedCount = 0
        var lastFailure = "No session-control endpoint candidates were configured."
        for (candidate in candidates) {
            val payload = candidate.bodyFactory(normalizedAction)
            Log.i("HermesSessionCtl", "POST path=${candidate.path} body=${payload.toString().take(200)}")
            runCatching {
                transport.post(candidate.path, payload, session.accessToken)
            }.onSuccess { raw ->
                Log.i("HermesSessionCtl", "response path=${candidate.path} body=${raw.take(400)}")
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
                Log.w("HermesSessionCtl", "failure path=${candidate.path} error=$message")
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

    override suspend fun fetchModels(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<HermesModel> =
        fetchCapabilityListing("/${HermesApiPaths.MODELS}", "models", session.accessToken) { it.toModel() }

    override suspend fun fetchSkills(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<com.hermescourier.android.domain.model.HermesSkill> =
        fetchCapabilityListing("/${HermesApiPaths.SKILLS}", "skills", session.accessToken) { it.toSkill() }

    override suspend fun saveSkill(session: HermesAuthSession, name: String, content: String, category: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("name", name).put("content", content)
            if (category.isNotBlank()) body.put("category", category)
            val response = transport.post("/${HermesApiPaths.SKILLS_SAVE}", body, session.accessToken)
            response.toJsonObject().optBoolean("ok", false)
        }

    override suspend fun deleteSkill(session: HermesAuthSession, name: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("name", name)
            val response = transport.post("/${HermesApiPaths.SKILLS_DELETE}", body, session.accessToken)
            response.toJsonObject().optBoolean("ok", false)
        }

    override suspend fun fetchSkillContent(session: HermesAuthSession, name: String, category: String): String? =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("name", name)
            if (category.isNotBlank()) body.put("category", category)
            runCatching {
                val response = transport.post("/${HermesApiPaths.SKILL_CONTENT}", body, session.accessToken)
                response.toJsonObject().optString("content", "").takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    override suspend fun fetchMemory(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<com.hermescourier.android.domain.model.HermesMemoryItem> =
        fetchCapabilityListing("/${HermesApiPaths.MEMORY}", "memory", session.accessToken) { it.toMemoryItem() }

    override suspend fun fetchCronJobs(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<com.hermescourier.android.domain.model.HermesCronJob> =
        fetchCapabilityListing("/${HermesApiPaths.CRON}", "cron", session.accessToken) { it.toCronJob() }

    override suspend fun fetchLogs(
        session: HermesAuthSession,
        limit: Int?,
        severity: String?,
    ): com.hermescourier.android.domain.model.HermesCapabilityListing<com.hermescourier.android.domain.model.HermesLogEntry> {
        val query = buildList {
            if (limit != null) add("limit=$limit")
            if (!severity.isNullOrBlank()) add("severity=${severity.lowercase()}")
        }.joinToString(separator = "&")
        val path = if (query.isEmpty()) "/${HermesApiPaths.LOGS}" else "/${HermesApiPaths.LOGS}?$query"
        return fetchCapabilityListing(path, "logs", session.accessToken) { it.toLogEntry() }
    }

    private fun buildConversationPath(sessionId: String?): String {
        val base = "/${HermesApiPaths.CONVERSATION}"
        val trimmed = sessionId?.trim()?.takeIf { it.isNotBlank() } ?: return base
        val encoded = java.net.URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        return "$base?sessionId=$encoded"
    }

    override suspend fun fetchConversation(
        session: HermesAuthSession,
        sessionId: String?,
    ): List<HermesConversationEvent> = withContext(Dispatchers.IO) {
        val path = buildConversationPath(sessionId)
        transport.get(path, session.accessToken).toJsonArrayOrObject().toConversationList()
    }

    override suspend fun submitConversationMessage(
        session: HermesAuthSession,
        message: String,
        sessionId: String?,
        model: String?,
    ): HermesConversationEvent? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("body", message).put("author", "You")
        if (!sessionId.isNullOrBlank()) {
            body.put("sessionId", sessionId)
        }
        
        val path = "/${HermesApiPaths.CONVERSATION}"

        if (!model.isNullOrBlank()) {
            body.put("model", model)
        }

        println("Hermes: POST $path body: $body")
        val response = transport.post(path, body, session.accessToken)
        println("Hermes: POST $path response: $response")
        
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

    /**
     * Fetches a list-or-unavailable capability endpoint. `404` is treated as an
     * unsupported signal (gateway has no such route); `5xx` and network errors
     * are surfaced to the caller so the UI can retry. Bodies with the
     * `UnavailablePayload` shape are honoured as terminal.
     */
    private suspend fun <T> fetchCapabilityListing(
        path: String,
        kind: String,
        bearer: String,
        mapObject: (JSONObject) -> T,
    ): com.hermescourier.android.domain.model.HermesCapabilityListing<T> = withContext(Dispatchers.IO) {
        println("Hermes: fetchCapabilityListing start path=$path kind=$kind")
        val outcome = runCatching { transport.get(path, bearer) }
        if (outcome.isSuccess) {
            val body = outcome.getOrThrow()
            println("Hermes: fetchCapabilityListing success path=$path")
            return@withContext parseCapabilityListing(body, mapObject)
        }
        val error = outcome.exceptionOrNull()
        println("Hermes: fetchCapabilityListing FAILED path=$path error=$error")
        val message = error?.message ?: error?.toString().orEmpty()
        val is404 = message.contains(" 404:")
        val is405 = message.contains(" 405:")
        val is501 = message.contains(" 501:")
        if (is404 || is405 || is501) {
            val detail = when {
                is404 -> "Gateway does not expose $path (HTTP 404)."
                is405 -> "Gateway rejected GET $path (HTTP 405)."
                else -> "Gateway returned HTTP 501 for $path."
            }
            return@withContext com.hermescourier.android.domain.model.HermesCapabilityListing(
                items = emptyList(),
                unavailable = com.hermescourier.android.domain.model.HermesUnavailablePayload(
                    type = "${kind}_unavailable",
                    detail = detail,
                    endpoint = path,
                ),
            )
        }
        throw error ?: IllegalStateException("Unknown failure fetching $path")
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
        val session = boot.getOrThrow()
        checks += HermesEndpointVerificationResult("auth/bootstrap", "ok", "Session ${session.sessionId} ready.")
        checks += runCheck("dashboard") { fetchDashboard(session) }
        val sessionsOutcome = runCatching { fetchSessions(session) }
        if (sessionsOutcome.isSuccess) {
            val sessions = sessionsOutcome.getOrThrow()
            checks += HermesEndpointVerificationResult("sessions list", "ok", "Fetched ${sessions.size} sessions.")
            if (sessions.isEmpty()) {
                checks += HermesEndpointVerificationResult("session detail", "skipped", "No sessions available to verify detail endpoint.")
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
        checks += runCheck("conversation list") { fetchConversation(session, sessionId = null) }
        checks += runCheck("conversation send") {
            submitConversationMessage(session, "live verification ping", sessionId = null, model = null)
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
                val wsUrl = configuration.baseUrl.newBuilder().addPathSegments(HermesApiPaths.EVENTS_STREAM).build()
                val request = Request.Builder()
                    .url(wsUrl)
                    .addHeader("Authorization", "Bearer ${session.accessToken}")
                    .build()
                onStatus(if (attempt == 0) "Realtime stream connecting" else "Realtime stream reconnecting")
                Log.i(TAG, "connect attempt=$attempt url=$wsUrl tokenExpiresAt=${session.expiresAt}")
                val connectStartedAtMs = System.currentTimeMillis()
                currentSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        attempt = 0
                        val elapsed = System.currentTimeMillis() - connectStartedAtMs
                        val protocol = response.protocol.toString()
                        val upgradeHeader = response.header("Upgrade") ?: "<none>"
                        val connectionHeader = response.header("Connection") ?: "<none>"
                        val serverHeader = response.header("Server") ?: "<none>"
                        Log.i(
                            TAG,
                            "onOpen code=${response.code} proto=$protocol upgrade=$upgradeHeader connection=$connectionHeader server=$serverHeader elapsedMs=$elapsed",
                        )
                        onStatus("Realtime stream connected")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d(TAG, "onMessage bytes=${text.length}")
                        runCatching { text.toJsonObject().toRealtimeEnvelope() }
                            .onSuccess(onEnvelope)
                            .onFailure {
                                Log.w(TAG, "parse error", it)
                                onStatus("Realtime parse error: ${it.message ?: "unknown"}")
                            }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        val elapsed = System.currentTimeMillis() - connectStartedAtMs
                        val httpCode = response?.code ?: -1
                        val responseBody = runCatching { response?.body?.string().orEmpty() }.getOrDefault("")
                        Log.w(
                            TAG,
                            "onFailure httpCode=$httpCode elapsedMs=$elapsed exception=${t.javaClass.simpleName} message=${t.message} fullBody=$responseBody",
                            t,
                        )
                        val parsedUnavailable = parseUnavailableBody(responseBody)
                        if (parsedUnavailable != null && !parsedUnavailable.supported) {
                            val detail = parsedUnavailable.detail.ifBlank { "Realtime stream not supported by this gateway" }
                            val fallbackHint = parsedUnavailable.fallbackPollEndpoints.joinToString(", ")
                            Log.i(TAG, "gateway signalled unsupported; halting reconnect loop detail=$detail fallbackPollEndpoints=$fallbackHint")
                            val statusMessage = if (fallbackHint.isNotEmpty()) {
                                "Realtime unsupported by gateway (polling fallback: $fallbackHint)"
                            } else {
                                "Realtime unsupported by gateway: $detail"
                            }
                            onStatus(statusMessage)
                            cancelled = true
                        } else {
                            onStatus("Realtime stream error: ${t.message ?: "unknown"}")
                        }
                        completed.complete(-1)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "onClosing code=$code reason=$reason")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        val elapsed = System.currentTimeMillis() - connectStartedAtMs
                        Log.i(TAG, "onClosed code=$code reason=$reason elapsedMs=$elapsed")
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
                Log.i(TAG, "backoff attempt=$attempt seconds=$backoffSeconds")
                onStatus("Realtime reconnecting in ${backoffSeconds}s")
                delay(backoffSeconds * 1000)
            }
            Log.i(TAG, "loop exiting cancelled=$cancelled isActive=$isActive")
        }
    }

    private data class GatewayUnavailable(
        val supported: Boolean,
        val detail: String,
        val fallbackPollEndpoints: List<String>,
    )

    private fun parseUnavailableBody(body: String): GatewayUnavailable? {
        if (body.isBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            val type = json.optString("type")
            val supported = json.optBoolean("supported", true)
            val isUnavailable = type == "events_unavailable" || !supported
            if (!isUnavailable) return@runCatching null
            val detail = json.optString("detail", "")
            val fallbackArray = json.optJSONArray("fallbackPollEndpoints")
            val fallback = buildList {
                if (fallbackArray != null) {
                    for (i in 0 until fallbackArray.length()) {
                        val value = fallbackArray.optString(i, "")
                        if (value.isNotBlank()) add(value)
                    }
                }
            }
            GatewayUnavailable(supported = supported, detail = detail, fallbackPollEndpoints = fallback)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "HermesRealtime"
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

    override suspend fun fetchConversation(
        session: HermesAuthSession,
        sessionId: String?,
    ): List<HermesConversationEvent> = listOf(
        HermesConversationEvent("event-01", "Hermes", "Awaiting your next instruction.", "now", sessionId),
        HermesConversationEvent("event-02", "You", "Review the latest approvals.", "just now", sessionId),
        HermesConversationEvent("event-03", "Hermes", "I found 2 pending approval requests.", "just now", sessionId),
    )

    override suspend fun submitConversationMessage(
        session: HermesAuthSession,
        message: String,
        sessionId: String?,
        model: String?,
    ): HermesConversationEvent? = HermesConversationEvent(
        eventId = "demo-${System.currentTimeMillis()}",
        author = "You",
        body = message.trim(),
        timestamp = "now",
        sessionId = sessionId,
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

    override suspend fun fetchModels(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<HermesModel> = com.hermescourier.android.domain.model.HermesCapabilityListing(
        items = listOf(
            HermesModel(id = "demo-model-fast", name = "Demo Fast Model"),
            HermesModel(id = "demo-model-smart", name = "Demo Deep Model"),
        )
    )

    override suspend fun fetchSkills(session: HermesAuthSession): com.hermescourier.android.domain.model.HermesCapabilityListing<com.hermescourier.android.domain.model.HermesSkill> = com.hermescourier.android.domain.model.HermesCapabilityListing(
        items = listOf(
            com.hermescourier.android.domain.model.HermesSkill(
                skillId = "demo-skill-web-search",
                name = "Web search",
                description = "Runs a web query against the configured search provider.",
                enabled = true,
                version = "1.0.0",
                scopes = listOf("net:read"),
            ),
            com.hermescourier.android.domain.model.HermesSkill(
                skillId = "demo-skill-repo",
                name = "Local repo",
                description = "Reads/writes files in the paired development workspace.",
                enabled = true,
                version = "0.9.0",
                scopes = listOf("fs:read", "fs:write"),
            ),
            com.hermescourier.android.domain.model.HermesSkill(
                skillId = "demo-skill-notify",
                name = "Notify",
                description = "Sends a push notification to this device.",
                enabled = false,
            ),
        ),
    )

    override suspend fun saveSkill(session: HermesAuthSession, name: String, content: String, category: String): Boolean = true
    override suspend fun deleteSkill(session: HermesAuthSession, name: String): Boolean = true
    override suspend fun fetchSkillContent(session: HermesAuthSession, name: String, category: String): String? = null

    override suspend fun fetchMemory(session: HermesAuthSession) = com.hermescourier.android.domain.model.HermesCapabilityListing(
        items = listOf(
            com.hermescourier.android.domain.model.HermesMemoryItem(
                memoryId = "demo-memory-pos",
                title = "orderking-pos-observer context",
                snippet = "Primary target repo, host-app integration in progress.",
                body = "orderking-pos-observer is the primary demo target. Hermes is currently implementing the host-app integration step.",
                tags = listOf("project", "active"),
                updatedAt = "just now",
                pinned = true,
            ),
            com.hermescourier.android.domain.model.HermesMemoryItem(
                memoryId = "demo-memory-pref",
                title = "Operator preferences",
                snippet = "Prefers concise status reports and reversible session actions.",
                tags = listOf("operator"),
                updatedAt = "yesterday",
            ),
        ),
    )

    override suspend fun fetchCronJobs(session: HermesAuthSession) = com.hermescourier.android.domain.model.HermesCapabilityListing(
        items = listOf(
            com.hermescourier.android.domain.model.HermesCronJob(
                cronId = "demo-cron-digest",
                name = "Daily digest",
                schedule = "0 9 * * *",
                enabled = true,
                description = "Summarise active sessions and pending approvals each morning.",
                lastRunAt = "today 09:00",
                lastStatus = "ok",
            ),
            com.hermescourier.android.domain.model.HermesCronJob(
                cronId = "demo-cron-sweep",
                name = "Approval sweep",
                schedule = "*/15 * * * *",
                enabled = false,
                description = "Poll for stale approvals every 15 minutes when enabled.",
                lastStatus = "paused",
            ),
        ),
    )

    override suspend fun fetchLogs(session: HermesAuthSession, limit: Int?, severity: String?) = com.hermescourier.android.domain.model.HermesCapabilityListing(
        items = listOf(
            com.hermescourier.android.domain.model.HermesLogEntry(
                logId = "demo-log-1",
                severity = "info",
                timestamp = "just now",
                message = "Demo log: gateway bootstrap completed.",
                source = "demo",
            ),
            com.hermescourier.android.domain.model.HermesLogEntry(
                logId = "demo-log-2",
                severity = "warn",
                timestamp = "2m ago",
                message = "Demo log: approval queue fell back to local storage.",
                source = "demo",
            ),
        ),
    )
}

internal fun HermesAuthChallengeResponse.toJson(): JSONObject = JSONObject()
    .put("challengeId", challengeId)
    .put("nonce", nonce)
    .put("expiresAt", expiresAt)
    .put("trustLevel", trustLevel)

internal fun HermesAuthChallengeRequest.toJson(): JSONObject = JSONObject()
    .put("device", device.toJson())
    .put("nonce", nonce)

internal fun HermesAuthResponseRequest.toJson(): JSONObject = JSONObject()
    .put("challengeId", challengeId)
    .put("signedNonce", signedNonce)
    .put("device", device.toJson())

internal fun HermesDeviceIdentity.toJson(): JSONObject = JSONObject()
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

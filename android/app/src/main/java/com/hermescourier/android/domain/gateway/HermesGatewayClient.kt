
package com.hermescourier.android.domain.gateway

import com.hermescourier.android.domain.auth.HermesChallengeSigner
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesAuthChallengeRequest
import com.hermescourier.android.domain.model.HermesAuthChallengeResponse
import com.hermescourier.android.domain.model.HermesAuthResponseRequest
import com.hermescourier.android.domain.model.HermesAuthSession
import com.hermescourier.android.domain.model.HermesConversationEvent
import com.hermescourier.android.domain.model.HermesDashboardSnapshot
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import com.hermescourier.android.domain.model.HermesSessionSummary
import com.hermescourier.android.domain.storage.HermesTokenStore
import com.hermescourier.android.domain.transport.HermesGatewayTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface HermesGatewayClient {
    suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession
    suspend fun fetchDashboard(session: HermesAuthSession): HermesDashboardSnapshot
    suspend fun fetchSessions(session: HermesAuthSession): List<HermesSessionSummary>
    suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary>
    suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent>
}

class NetworkHermesGatewayClient(
    private val transport: HermesGatewayTransport,
    private val tokenStore: HermesTokenStore,
    private val signer: HermesChallengeSigner,
) : HermesGatewayClient {
    override suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession = withContext(Dispatchers.IO) {
        val challenge = requestChallenge(device)
        val signedNonce = signer.sign(challenge.nonce, device)
        val response = transport.post(
            path = "/v1/auth/response",
            body = HermesAuthResponseRequest(
                challengeId = challenge.challengeId,
                signedNonce = signedNonce,
                device = device,
            ).toJson(),
        )
        val session = response.toSession()
        tokenStore.save(session)
        session
    }

    override suspend fun fetchDashboard(session: HermesAuthSession): HermesDashboardSnapshot = withContext(Dispatchers.IO) {
        transport.get("/v1/dashboard", session.accessToken).toJsonObject().toDashboard()
    }

    override suspend fun fetchSessions(session: HermesAuthSession): List<HermesSessionSummary> = withContext(Dispatchers.IO) {
        transport.get("/v1/sessions", session.accessToken).toJsonArrayOrObject().toSessionList()
    }

    override suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary> = withContext(Dispatchers.IO) {
        transport.get("/v1/approvals", session.accessToken).toJsonArrayOrObject().toApprovalList()
    }

    override suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent> = withContext(Dispatchers.IO) {
        transport.get("/v1/events", session.accessToken).toJsonArrayOrObject().toConversationList()
    }

    private suspend fun requestChallenge(device: HermesDeviceIdentity): HermesAuthChallengeResponse {
        val response = transport.post(
            path = "/v1/auth/challenge",
            body = HermesAuthChallengeRequest(device = device, nonce = buildNonce(device)).toJson(),
        )
        return response.toJsonObject().toChallenge()
    }

    private fun buildNonce(device: HermesDeviceIdentity): String =
        "${device.deviceId}:${signer.publicKeyFingerprint()}:${System.currentTimeMillis()}"
}

class DemoHermesGatewayClient : HermesGatewayClient {
    override suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession {
        return HermesAuthSession(
            sessionId = "session-${device.deviceId.takeLast(6)}",
            accessToken = "demo-access-token",
            refreshToken = "demo-refresh-token",
            expiresAt = "2026-04-19T20:15:00Z",
            gatewayUrl = "https://gateway.hermes.local",
            mtlsRequired = true,
            scope = listOf("dashboard:read", "sessions:read", "approvals:write", "events:read"),
        )
    }

    override suspend fun fetchDashboard(session: HermesAuthSession): HermesDashboardSnapshot {
        return HermesDashboardSnapshot(
            activeSessionCount = 1,
            pendingApprovalCount = 2,
            lastSyncLabel = "12 seconds ago",
            connectionState = "Connected to ${session.gatewayUrl}",
        )
    }

    override suspend fun fetchSessions(session: HermesAuthSession): List<HermesSessionSummary> = listOf(
        HermesSessionSummary("session-01", "Build agent", "running", "18m ago"),
        HermesSessionSummary("session-02", "Research agent", "idle", "3h ago"),
        HermesSessionSummary("session-03", "Deployment agent", "waiting approval", "now"),
    )

    override suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary> = listOf(
        HermesApprovalSummary("approval-01", "Send message to Slack #ops", "Sensitive external message", true),
        HermesApprovalSummary("approval-02", "Restart long-running task", "May interrupt progress", true),
    )

    override suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent> = listOf(
        HermesConversationEvent("event-01", "Hermes", "Awaiting your next instruction.", "now"),
        HermesConversationEvent("event-02", "You", "Review the latest approvals.", "just now"),
        HermesConversationEvent("event-03", "Hermes", "I found 2 pending approval requests.", "just now"),
    )
}

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

private fun String.toJsonObject(): JSONObject = JSONObject(this)

private fun String.toJsonArrayOrObject(): JSONArray = when (trim().firstOrNull()) {
    '[' -> JSONArray(this)
    else -> JSONObject(this).optJSONArray("items") ?: JSONArray()
}

private fun JSONObject.toChallenge(): HermesAuthChallengeResponse = HermesAuthChallengeResponse(
    challengeId = getString("challengeId"),
    nonce = getString("nonce"),
    expiresAt = getString("expiresAt"),
    trustLevel = getString("trustLevel"),
)

private fun JSONObject.toSession(): HermesAuthSession = HermesAuthSession(
    sessionId = getString("sessionId"),
    accessToken = getString("accessToken"),
    refreshToken = getString("refreshToken"),
    expiresAt = getString("expiresAt"),
    gatewayUrl = getString("gatewayUrl"),
    mtlsRequired = optBoolean("mtlsRequired", false),
    scope = optJSONArray("scope")?.toStringList() ?: emptyList(),
)

private fun JSONObject.toDashboard(): HermesDashboardSnapshot = HermesDashboardSnapshot(
    activeSessionCount = getInt("activeSessionCount"),
    pendingApprovalCount = getInt("pendingApprovalCount"),
    lastSyncLabel = getString("lastSyncLabel"),
    connectionState = getString("connectionState"),
)

private fun JSONArray.toSessionList(): List<HermesSessionSummary> = buildList {
    for (index in 0 until length()) {
        val json = getJSONObject(index)
        add(
            HermesSessionSummary(
                sessionId = json.getString("sessionId"),
                title = json.getString("title"),
                status = json.getString("status"),
                updatedAt = json.getString("updatedAt"),
            )
        )
    }
}

private fun JSONArray.toApprovalList(): List<HermesApprovalSummary> = buildList {
    for (index in 0 until length()) {
        val json = getJSONObject(index)
        add(
            HermesApprovalSummary(
                approvalId = json.getString("approvalId"),
                title = json.getString("title"),
                detail = json.getString("detail"),
                requiresBiometrics = json.getBoolean("requiresBiometrics"),
            )
        )
    }
}

private fun JSONArray.toConversationList(): List<HermesConversationEvent> = buildList {
    for (index in 0 until length()) {
        val json = getJSONObject(index)
        add(
            HermesConversationEvent(
                eventId = json.getString("eventId"),
                author = json.getString("author"),
                body = json.getString("body"),
                timestamp = json.getString("timestamp"),
            )
        )
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) {
        add(getString(index))
    }
}

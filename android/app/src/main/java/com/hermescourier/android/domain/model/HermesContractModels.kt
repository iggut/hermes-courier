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

data class HermesApprovalActionRequest(
    val approvalId: String,
    val action: String,
    val note: String? = null,
)

data class HermesApprovalActionResult(
    val approvalId: String,
    val action: String,
    val status: String,
    val detail: String,
    val updatedAt: String,
)

data class HermesRealtimeEnvelope(
    val type: String,
    val dashboard: HermesDashboardSnapshot? = null,
    val sessions: List<HermesSessionSummary>? = null,
    val approvals: List<HermesApprovalSummary>? = null,
    val conversation: HermesConversationEvent? = null,
    val approvalResult: HermesApprovalActionResult? = null,
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
)

data class HermesQueuedApprovalAction(
    val approvalId: String,
    val action: String,
    val note: String?,
    val createdAt: Long,
)

data class HermesCourierUiState(
    val bootstrapState: String = "Bootstrapping secure gateway",
    val authStatus: String = "Waiting for device-bound challenge",
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
    val approvalActionStatus: String = "No approval action submitted",
    val streamStatus: String = "Realtime stream disconnected",
    val gatewaySettings: HermesGatewaySettings = HermesGatewaySettings(),
    val deviceFingerprint: String = "pending-device-enrollment",
    val enrollmentStatus: String = "No certificate imported yet",
    val enrollmentQrPayload: String = "",
    val queuedApprovalActions: Int = 0,
    val queuedApprovalActionQueue: List<HermesQueuedApprovalAction> = emptyList(),
)

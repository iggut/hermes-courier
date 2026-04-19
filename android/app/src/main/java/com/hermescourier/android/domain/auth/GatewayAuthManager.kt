
package com.hermescourier.android.domain.auth

import com.hermescourier.android.domain.model.HermesAuthChallengeRequest
import com.hermescourier.android.domain.model.HermesAuthChallengeResponse
import com.hermescourier.android.domain.model.HermesAuthSession
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import kotlinx.coroutines.delay

data class SecureGatewayBootstrap(
    val session: HermesAuthSession,
    val bootstrapState: String,
    val authStatus: String,
)

interface GatewayAuthManager {
    suspend fun bootstrap(device: HermesDeviceIdentity): SecureGatewayBootstrap
}

class DemoGatewayAuthManager : GatewayAuthManager {
    override suspend fun bootstrap(device: HermesDeviceIdentity): SecureGatewayBootstrap {
        val challengeRequest = HermesAuthChallengeRequest(
            device = device,
            nonce = "nonce-${device.deviceId.takeLast(8)}",
        )
        delay(50)
        val challengeResponse = HermesAuthChallengeResponse(
            challengeId = "challenge-${device.deviceId.takeLast(6)}",
            nonce = challengeRequest.nonce,
            expiresAt = "2026-04-19T19:15:00Z",
            trustLevel = "trusted",
        )
        delay(50)
        val session = HermesAuthSession(
            sessionId = "session-${device.deviceId.takeLast(6)}",
            accessToken = "demo-access-token-${challengeResponse.challengeId}",
            refreshToken = "demo-refresh-token-${challengeResponse.challengeId}",
            expiresAt = "2026-04-19T20:15:00Z",
            gatewayUrl = "https://gateway.hermes.local",
            mtlsRequired = true,
            scope = listOf("dashboard:read", "sessions:read", "approvals:write", "events:read"),
        )
        return SecureGatewayBootstrap(
            session = session,
            bootstrapState = "mTLS session established",
            authStatus = "Trusted device challenge completed (${challengeResponse.trustLevel})",
        )
    }
}

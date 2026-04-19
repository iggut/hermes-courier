
package com.hermescourier.android.domain.gateway

import com.hermescourier.android.domain.auth.DemoGatewayAuthManager
import com.hermescourier.android.domain.auth.GatewayAuthManager
import com.hermescourier.android.domain.model.HermesApprovalSummary
import com.hermescourier.android.domain.model.HermesAuthSession
import com.hermescourier.android.domain.model.HermesConversationEvent
import com.hermescourier.android.domain.model.HermesDashboardSnapshot
import com.hermescourier.android.domain.model.HermesDeviceIdentity
import com.hermescourier.android.domain.model.HermesSessionSummary
import kotlinx.coroutines.delay

interface HermesGatewayClient {
    suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession
    suspend fun fetchDashboard(session: HermesAuthSession): HermesDashboardSnapshot
    suspend fun fetchSessions(session: HermesAuthSession): List<HermesSessionSummary>
    suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary>
    suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent>
}

class DemoHermesGatewayClient(
    private val authManager: GatewayAuthManager = DemoGatewayAuthManager(),
) : HermesGatewayClient {
    override suspend fun bootstrap(device: HermesDeviceIdentity): HermesAuthSession {
        return authManager.bootstrap(device).session
    }

    override suspend fun fetchDashboard(session: HermesAuthSession): HermesDashboardSnapshot {
        delay(50)
        return HermesDashboardSnapshot(
            activeSessionCount = 1,
            pendingApprovalCount = 2,
            lastSyncLabel = "12 seconds ago",
            connectionState = "Connected to ${session.gatewayUrl}",
        )
    }

    override suspend fun fetchSessions(session: HermesAuthSession): List<HermesSessionSummary> {
        delay(50)
        return listOf(
            HermesSessionSummary("session-01", "Build agent", "running", "18m ago"),
            HermesSessionSummary("session-02", "Research agent", "idle", "3h ago"),
            HermesSessionSummary("session-03", "Deployment agent", "waiting approval", "now"),
        )
    }

    override suspend fun fetchApprovals(session: HermesAuthSession): List<HermesApprovalSummary> {
        delay(50)
        return listOf(
            HermesApprovalSummary("approval-01", "Send message to Slack #ops", "Sensitive external message", true),
            HermesApprovalSummary("approval-02", "Restart long-running task", "May interrupt progress", true),
        )
    }

    override suspend fun fetchConversation(session: HermesAuthSession): List<HermesConversationEvent> {
        delay(50)
        return listOf(
            HermesConversationEvent("event-01", "Hermes", "Awaiting your next instruction.", "now"),
            HermesConversationEvent("event-02", "You", "Review the latest approvals.", "just now"),
            HermesConversationEvent("event-03", "Hermes", "I found 2 pending approval requests.", "just now"),
        )
    }
}

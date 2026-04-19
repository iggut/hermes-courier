
import Foundation

protocol HermesGatewayClientProtocol {
    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot
    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary]
    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary]
    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent]
}

final class HermesGatewayClient: HermesGatewayClientProtocol {
    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot {
        try await Task.sleep(nanoseconds: 50_000_000)
        return HermesDashboardSnapshot(
            activeSessionCount: 1,
            pendingApprovalCount: 2,
            lastSyncLabel: "12 seconds ago",
            connectionState: "Connected to \(session.gatewayUrl)"
        )
    }

    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary] {
        try await Task.sleep(nanoseconds: 50_000_000)
        return [
            HermesSessionSummary(sessionId: "session-01", title: "Build agent", status: "running", updatedAt: "18m ago"),
            HermesSessionSummary(sessionId: "session-02", title: "Research agent", status: "idle", updatedAt: "3h ago"),
            HermesSessionSummary(sessionId: "session-03", title: "Deployment agent", status: "waiting approval", updatedAt: "now"),
        ]
    }

    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary] {
        try await Task.sleep(nanoseconds: 50_000_000)
        return [
            HermesApprovalSummary(approvalId: "approval-01", title: "Send message to Slack #ops", detail: "Sensitive external message", requiresBiometrics: true),
            HermesApprovalSummary(approvalId: "approval-02", title: "Restart long-running task", detail: "May interrupt progress", requiresBiometrics: true),
        ]
    }

    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent] {
        try await Task.sleep(nanoseconds: 50_000_000)
        return [
            HermesConversationEvent(eventId: "event-01", author: "Hermes", body: "Awaiting your next instruction.", timestamp: "now"),
            HermesConversationEvent(eventId: "event-02", author: "You", body: "Review the latest approvals.", timestamp: "just now"),
            HermesConversationEvent(eventId: "event-03", author: "Hermes", body: "I found 2 pending approval requests.", timestamp: "just now"),
        ]
    }
}

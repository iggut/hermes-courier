
import Foundation

@MainActor
final class AppViewModel: ObservableObject {
    @Published var bootstrapState: String = "Bootstrapping secure gateway"
    @Published var authStatus: String = "Waiting for device-bound challenge"
    @Published var dashboard = HermesDashboardSnapshot(
        activeSessionCount: 0,
        pendingApprovalCount: 0,
        lastSyncLabel: "Never",
        connectionState: "Disconnected"
    )
    @Published var sessions: [HermesSessionSummary] = []
    @Published var approvals: [HermesApprovalSummary] = []
    @Published var messages: [HermesConversationEvent] = [
        HermesConversationEvent(eventId: "boot-1", author: "Hermes", body: "Awaiting secure gateway bootstrap.", timestamp: "now")
    ]

    private let authManager: HermesAuthManaging
    private let gatewayClient: HermesGatewayClientProtocol

    init(
        authManager: HermesAuthManaging = HermesAuthManager(),
        gatewayClient: HermesGatewayClientProtocol = HermesGatewayClient()
    ) {
        self.authManager = authManager
        self.gatewayClient = gatewayClient
        Task {
            await refresh()
        }
    }

    func refresh() async {
        bootstrapState = "Negotiating secure gateway"
        authStatus = "Requesting device challenge"

        do {
            let session = try await authManager.bootstrapSession()
            bootstrapState = "Secure gateway ready"
            authStatus = "Session \(session.sessionId) authenticated through \(session.gatewayUrl)"
            dashboard = try await gatewayClient.fetchDashboard(session: session)
            sessions = try await gatewayClient.fetchSessions(session: session)
            approvals = try await gatewayClient.fetchApprovals(session: session)
            messages = try await gatewayClient.fetchConversation(session: session)
        } catch {
            bootstrapState = "Demo fallback active"
            authStatus = "Using offline-safe sample data (\(error.localizedDescription))"
        }
    }
}

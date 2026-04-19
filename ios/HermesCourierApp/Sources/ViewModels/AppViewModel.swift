
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
    private let fallbackClient: HermesGatewayClientProtocol

    init(
        authManager: HermesAuthManaging = HermesAuthManager(),
        gatewayClient: HermesGatewayClientProtocol = HermesGatewayClient(),
        fallbackClient: HermesGatewayClientProtocol = HermesDemoGatewayClient()
    ) {
        self.authManager = authManager
        self.gatewayClient = gatewayClient
        self.fallbackClient = fallbackClient
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
            do {
                let session = try await fallbackBootstrap()
                bootstrapState = "Demo fallback active"
                authStatus = "Using offline-safe sample data (\(error.localizedDescription))"
                dashboard = try await fallbackClient.fetchDashboard(session: session)
                sessions = try await fallbackClient.fetchSessions(session: session)
                approvals = try await fallbackClient.fetchApprovals(session: session)
                messages = try await fallbackClient.fetchConversation(session: session)
            } catch {
                authStatus = "Fallback failed: \(error.localizedDescription)"
            }
        }
    }

    private func fallbackBootstrap() async throws -> HermesAuthSession {
        try await fallbackClient.bootstrap(device: HermesDeviceIdentity(
            deviceId: "ios-demo-device-001",
            platform: "ios",
            appVersion: "0.1.0",
            publicKeyFingerprint: "demo-fingerprint"
        ))
    }
}

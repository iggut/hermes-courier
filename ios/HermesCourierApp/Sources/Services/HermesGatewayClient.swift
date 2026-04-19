
import Foundation

protocol HermesGatewayClientProtocol {
    func bootstrap(device: HermesDeviceIdentity) async throws -> HermesAuthSession
    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot
    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary]
    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary]
    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent]
}

final class HermesGatewayClient: HermesGatewayClientProtocol {
    private let transport: HermesHTTPTransport
    private let tokenStore: HermesTokenStoring
    private let signer: HermesChallengeSigning

    init(
        transport: HermesHTTPTransport = HermesURLSessionTransport(configuration: HermesGatewayConfiguration.load()),
        tokenStore: HermesTokenStoring = HermesKeychainTokenStore(),
        signer: HermesChallengeSigning = HermesKeychainChallengeSigner()
    ) {
        self.transport = transport
        self.tokenStore = tokenStore
        self.signer = signer
    }

    func bootstrap(device: HermesDeviceIdentity) async throws -> HermesAuthSession {
        let challenge = try await requestChallenge(device: device)
        let signedNonce = try signer.sign(nonce: challenge.nonce, device: device)
        let request = HermesAuthResponseRequest(
            challengeId: challenge.challengeId,
            signedNonce: signedNonce,
            device: device
        )
        let data = try await transport.post(
            path: "/v1/auth/response",
            body: JSONEncoder().encode(request),
            bearerToken: nil
        )
        let session = try JSONDecoder().decode(HermesAuthSession.self, from: data)
        try tokenStore.save(session)
        return session
    }

    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot {
        let data = try await transport.get(path: "/v1/dashboard", bearerToken: session.accessToken)
        return try JSONDecoder().decode(HermesDashboardSnapshot.self, from: data)
    }

    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary] {
        let data = try await transport.get(path: "/v1/sessions", bearerToken: session.accessToken)
        return try decodeCollection(data)
    }

    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary] {
        let data = try await transport.get(path: "/v1/approvals", bearerToken: session.accessToken)
        return try decodeCollection(data)
    }

    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent] {
        let data = try await transport.get(path: "/v1/events", bearerToken: session.accessToken)
        return try decodeCollection(data)
    }

    private func requestChallenge(device: HermesDeviceIdentity) async throws -> HermesAuthChallengeResponse {
        let request = HermesAuthChallengeRequest(device: device, nonce: buildNonce(device: device))
        let data = try await transport.post(path: "/v1/auth/challenge", body: JSONEncoder().encode(request), bearerToken: nil)
        return try JSONDecoder().decode(HermesAuthChallengeResponse.self, from: data)
    }

    private func buildNonce(device: HermesDeviceIdentity) -> String {
        "\(device.deviceId):\(device.publicKeyFingerprint):\(Int(Date().timeIntervalSince1970))"
    }

    private func decodeCollection<T: Decodable>(_ data: Data) throws -> [T] {
        let decoder = JSONDecoder()
        if let array = try? decoder.decode([T].self, from: data) {
            return array
        }
        struct ItemsWrapper<U: Decodable>: Decodable { let items: [U] }
        return try decoder.decode(ItemsWrapper<T>.self, from: data).items
    }
}

final class HermesDemoGatewayClient: HermesGatewayClientProtocol {
    func bootstrap(device: HermesDeviceIdentity) async throws -> HermesAuthSession {
        HermesAuthSession(
            sessionId: "session-\(device.deviceId.suffix(6))",
            accessToken: "demo-access-token",
            refreshToken: "demo-refresh-token",
            expiresAt: "2026-04-19T20:15:00Z",
            gatewayUrl: "https://gateway.hermes.local",
            mtlsRequired: true,
            scope: ["dashboard:read", "sessions:read", "approvals:write", "events:read"]
        )
    }

    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot {
        HermesDashboardSnapshot(
            activeSessionCount: 1,
            pendingApprovalCount: 2,
            lastSyncLabel: "12 seconds ago",
            connectionState: "Connected to \(session.gatewayUrl)"
        )
    }

    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary] {
        [
            HermesSessionSummary(sessionId: "session-01", title: "Build agent", status: "running", updatedAt: "18m ago"),
            HermesSessionSummary(sessionId: "session-02", title: "Research agent", status: "idle", updatedAt: "3h ago"),
            HermesSessionSummary(sessionId: "session-03", title: "Deployment agent", status: "waiting approval", updatedAt: "now"),
        ]
    }

    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary] {
        [
            HermesApprovalSummary(approvalId: "approval-01", title: "Send message to Slack #ops", detail: "Sensitive external message", requiresBiometrics: true),
            HermesApprovalSummary(approvalId: "approval-02", title: "Restart long-running task", detail: "May interrupt progress", requiresBiometrics: true),
        ]
    }

    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent] {
        [
            HermesConversationEvent(eventId: "event-01", author: "Hermes", body: "Awaiting your next instruction.", timestamp: "now"),
            HermesConversationEvent(eventId: "event-02", author: "You", body: "Review the latest approvals.", timestamp: "just now"),
            HermesConversationEvent(eventId: "event-03", author: "Hermes", body: "I found 2 pending approval requests.", timestamp: "just now"),
        ]
    }
}

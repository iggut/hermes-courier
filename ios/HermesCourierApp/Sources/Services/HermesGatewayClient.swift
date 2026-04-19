import Foundation

protocol HermesGatewayClientProtocol {
    func bootstrap(device: HermesDeviceIdentity) async throws -> HermesAuthSession
    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot
    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary]
    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary]
    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent]
    func submitApprovalAction(session: HermesAuthSession, approvalId: String, action: String, note: String?) async throws -> HermesApprovalActionResult
    func connectRealtime(session: HermesAuthSession, onStatus: @escaping (String) -> Void, onEnvelope: @escaping (HermesRealtimeEnvelope) -> Void) -> HermesRealtimeStreamHandle
}

final class HermesGatewayClient: HermesGatewayClientProtocol {
    private let transport: HermesURLSessionTransport
    private let tokenStore: HermesTokenStoring
    private let signer: HermesChallengeSigning

    init(
        transport: HermesURLSessionTransport = HermesURLSessionTransport(configuration: HermesGatewayConfiguration.load()),
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
        return try decodeCollection(HermesSessionSummary.self, from: data)
    }

    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary] {
        let data = try await transport.get(path: "/v1/approvals", bearerToken: session.accessToken)
        return try decodeCollection(HermesApprovalSummary.self, from: data)
    }

    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent] {
        let data = try await transport.get(path: "/v1/conversation", bearerToken: session.accessToken)
        return try decodeCollection(HermesConversationEvent.self, from: data)
    }

    func submitApprovalAction(session: HermesAuthSession, approvalId: String, action: String, note: String?) async throws -> HermesApprovalActionResult {
        let data = try await transport.post(
            path: "/v1/approvals/\(approvalId)/actions",
            body: JSONEncoder().encode(HermesApprovalActionRequest(approvalId: approvalId, action: action, note: note)),
            bearerToken: session.accessToken
        )
        return try JSONDecoder().decode(HermesApprovalActionResult.self, from: data)
    }

    func connectRealtime(session: HermesAuthSession, onStatus: @escaping (String) -> Void, onEnvelope: @escaping (HermesRealtimeEnvelope) -> Void) -> HermesRealtimeStreamHandle {
        let socket = transport.webSocketTask(path: "/v1/stream", bearerToken: session.accessToken)
        let handle = HermesRealtimeStreamHandle(socket: socket)
        handle.start(onStatus: onStatus, onEnvelope: onEnvelope)
        return handle
    }

    private func requestChallenge(device: HermesDeviceIdentity) async throws -> HermesAuthChallengeResponse {
        let request = HermesAuthChallengeRequest(device: device, nonce: UUID().uuidString)
        let data = try await transport.post(
            path: "/v1/auth/challenge",
            body: JSONEncoder().encode(request),
            bearerToken: nil
        )
        return try JSONDecoder().decode(HermesAuthChallengeResponse.self, from: data)
    }

    private func decodeCollection<T: Decodable>(_ type: T.Type, from data: Data) throws -> [T] {
        if let items = try? JSONDecoder().decode([T].self, from: data) {
            return items
        }
        if let wrapper = try? JSONDecoder().decode(HermesCollectionWrapper<T>.self, from: data) {
            return wrapper.items
        }
        return []
    }
}

final class HermesDemoGatewayClient: HermesGatewayClientProtocol {
    func bootstrap(device: HermesDeviceIdentity) async throws -> HermesAuthSession {
        HermesAuthSession(
            sessionId: "demo-session",
            accessToken: "demo-access-token",
            refreshToken: "demo-refresh-token",
            expiresAt: "2099-01-01T00:00:00Z",
            gatewayUrl: "https://demo.hermes.local",
            mtlsRequired: false,
            scope: ["dashboard:read", "sessions:read", "approvals:write", "events:read"]
        )
    }

    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot {
        HermesDashboardSnapshot(
            activeSessionCount: 2,
            pendingApprovalCount: 1,
            lastSyncLabel: "Just now",
            connectionState: "Demo fallback connected"
        )
    }

    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary] {
        [
            HermesSessionSummary(sessionId: "demo-1", title: "Morning planning", status: "Running", updatedAt: "2m ago"),
            HermesSessionSummary(sessionId: "demo-2", title: "Tooling review", status: "Paused", updatedAt: "15m ago"),
        ]
    }

    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary] {
        [
            HermesApprovalSummary(approvalId: "approval-1", title: "Deploy branch", detail: "Approve production deployment for feature work.", requiresBiometrics: true),
            HermesApprovalSummary(approvalId: "approval-2", title: "Share transcript", detail: "Allow sharing the latest conversation transcript.", requiresBiometrics: false),
        ]
    }

    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent] {
        [
            HermesConversationEvent(eventId: "event-01", author: "Hermes", body: "Awaiting your next instruction.", timestamp: "now"),
            HermesConversationEvent(eventId: "event-02", author: "You", body: "Review the latest approvals.", timestamp: "just now"),
            HermesConversationEvent(eventId: "event-03", author: "Hermes", body: "I found 2 pending approval requests.", timestamp: "just now"),
        ]
    }

    func submitApprovalAction(session: HermesAuthSession, approvalId: String, action: String, note: String?) async throws -> HermesApprovalActionResult {
        HermesApprovalActionResult(
            approvalId: approvalId,
            action: action,
            status: "demo-complete",
            detail: note ?? "Demo fallback accepted the approval action.",
            updatedAt: "just now"
        )
    }

    func connectRealtime(session: HermesAuthSession, onStatus: @escaping (String) -> Void, onEnvelope: @escaping (HermesRealtimeEnvelope) -> Void) -> HermesRealtimeStreamHandle {
        let handle = HermesRealtimeStreamHandle(socket: nil)
        onStatus("Realtime stream connected (demo)")
        onEnvelope(
            HermesRealtimeEnvelope(
                type: "conversation",
                dashboard: nil,
                sessions: nil,
                approvals: nil,
                conversation: HermesConversationEvent(eventId: "demo-stream-1", author: "Hermes", body: "Demo realtime stream active.", timestamp: "now"),
                approvalResult: nil
            )
        )
        return handle
    }
}

final class HermesRealtimeStreamHandle {
    private var socket: URLSessionWebSocketTask?
    private var task: Task<Void, Never>?

    init(socket: URLSessionWebSocketTask?) {
        self.socket = socket
    }

    func start(onStatus: @escaping (String) -> Void, onEnvelope: @escaping (HermesRealtimeEnvelope) -> Void) {
        guard let socket = socket else {
            return
        }
        socket.resume()
        task = Task {
            onStatus("Realtime stream connecting")
            onStatus("Realtime stream connected")
            while !Task.isCancelled {
                do {
                    let message = try await socket.receive()
                    switch message {
                    case .string(let text):
                        if let data = text.data(using: .utf8), let envelope = try? JSONDecoder().decode(HermesRealtimeEnvelope.self, from: data) {
                            onEnvelope(envelope)
                        } else if let data = text.data(using: .utf8), let conversation = try? JSONDecoder().decode(HermesConversationEvent.self, from: data) {
                            onEnvelope(HermesRealtimeEnvelope(type: "conversation", dashboard: nil, sessions: nil, approvals: nil, conversation: conversation, approvalResult: nil))
                        }
                    case .data(let data):
                        if let envelope = try? JSONDecoder().decode(HermesRealtimeEnvelope.self, from: data) {
                            onEnvelope(envelope)
                        }
                    @unknown default:
                        break
                    }
                } catch {
                    onStatus("Realtime stream error: \(error.localizedDescription)")
                    break
                }
            }
        }
    }

    func cancel() {
        task?.cancel()
        task = nil
        socket?.cancel(with: .goingAway, reason: nil)
        socket = nil
    }
}

private struct HermesCollectionWrapper<T: Decodable>: Decodable {
    let items: [T]
}

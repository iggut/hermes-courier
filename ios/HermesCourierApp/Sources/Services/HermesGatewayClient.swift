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
        tokenStore: HermesKeychainTokenStore = HermesKeychainTokenStore(),
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
            path: HermesAPIPaths.authResponse,
            body: JSONEncoder().encode(request),
            bearerToken: nil
        )
        let session = try JSONDecoder().decode(HermesAuthSession.self, from: data)
        try tokenStore.save(session)
        return session
    }

    func fetchDashboard(session: HermesAuthSession) async throws -> HermesDashboardSnapshot {
        let data = try await transport.get(path: HermesAPIPaths.dashboard, bearerToken: session.accessToken)
        return try JSONDecoder().decode(HermesDashboardSnapshot.self, from: data)
    }

    func fetchSessions(session: HermesAuthSession) async throws -> [HermesSessionSummary] {
        let data = try await transport.get(path: HermesAPIPaths.sessions, bearerToken: session.accessToken)
        return try decodeCollection(HermesSessionSummary.self, from: data)
    }

    func fetchApprovals(session: HermesAuthSession) async throws -> [HermesApprovalSummary] {
        let data = try await transport.get(path: HermesAPIPaths.approvals, bearerToken: session.accessToken)
        return try decodeCollection(HermesApprovalSummary.self, from: data)
    }

    func fetchConversation(session: HermesAuthSession) async throws -> [HermesConversationEvent] {
        let data = try await transport.get(path: HermesAPIPaths.conversation, bearerToken: session.accessToken)
        return try decodeCollection(HermesConversationEvent.self, from: data)
    }

    func submitApprovalAction(session: HermesAuthSession, approvalId: String, action: String, note: String?) async throws -> HermesApprovalActionResult {
        let decision = Self.normalizeApprovalDecision(action)
        let data = try await transport.post(
            path: HermesAPIPaths.approvalDecision(approvalId: approvalId),
            body: JSONEncoder().encode(HermesApprovalDecisionBody(decision: decision, reason: note)),
            bearerToken: session.accessToken
        )
        if data.isEmpty {
            return HermesApprovalActionResult(
                approvalId: approvalId,
                action: decision,
                status: "recorded",
                detail: note ?? "Decision recorded.",
                updatedAt: "now"
            )
        }
        if let decoded = try? JSONDecoder().decode(HermesApprovalActionResult.self, from: data) {
            return decoded
        }
        return HermesApprovalActionResult(
            approvalId: approvalId,
            action: decision,
            status: "recorded",
            detail: note ?? "Decision recorded.",
            updatedAt: "now"
        )
    }

    func connectRealtime(session: HermesAuthSession, onStatus: @escaping (String) -> Void, onEnvelope: @escaping (HermesRealtimeEnvelope) -> Void) -> HermesRealtimeStreamHandle {
        let handle = HermesRealtimeStreamHandle()
        handle.start { [transport] streamHandle in
            await Self.runRealtimeLoop(
                transport: transport,
                session: session,
                handle: streamHandle,
                onStatus: onStatus,
                onEnvelope: onEnvelope
            )
        }
        return handle
    }

    private static func runRealtimeLoop(
        transport: HermesURLSessionTransport,
        session: HermesAuthSession,
        handle: HermesRealtimeStreamHandle,
        onStatus: @escaping (String) -> Void,
        onEnvelope: @escaping (HermesRealtimeEnvelope) -> Void
    ) async {
        var attempt = 0
        while !Task.isCancelled {
            let socket = transport.webSocketTask(path: HermesAPIPaths.eventsStream, bearerToken: session.accessToken)
            handle.register(socket: socket)
            onStatus(attempt == 0 ? "Realtime stream connecting" : "Realtime stream reconnecting")
            socket.resume()
            onStatus("Realtime stream connected")
            do {
                while !Task.isCancelled {
                    let message = try await socket.receive()
                    switch message {
                    case .string(let text):
                        try emitEnvelope(from: text, onEnvelope: onEnvelope, onStatus: onStatus)
                    case .data(let data):
                        if let text = String(data: data, encoding: .utf8) {
                            try emitEnvelope(from: text, onEnvelope: onEnvelope, onStatus: onStatus)
                        }
                    @unknown default:
                        break
                    }
                }
            } catch {
                if Task.isCancelled { break }
                onStatus("Realtime stream error: \(error.localizedDescription)")
            }
            if Task.isCancelled { break }
            socket.cancel(with: .goingAway, reason: nil)
            attempt = min(attempt + 1, 5)
            let backoffSeconds = min(pow(2.0, Double(attempt)), 30.0)
            onStatus("Realtime reconnecting in \(Int(backoffSeconds))s")
            try? await Task.sleep(nanoseconds: UInt64(backoffSeconds * 1_000_000_000))
        }
        onStatus("Realtime stream disconnected")
    }

    private static func emitEnvelope(from text: String, onEnvelope: @escaping (HermesRealtimeEnvelope) -> Void, onStatus: @escaping (String) -> Void) throws {
        let data = Data(text.utf8)
        do {
            let envelope = try JSONDecoder().decode(HermesRealtimeEnvelope.self, from: data)
            onEnvelope(envelope)
        } catch {
            onStatus("Realtime parse error: \(error.localizedDescription)")
        }
    }

    private func requestChallenge(device: HermesDeviceIdentity) async throws -> HermesAuthChallengeResponse {
        let request = HermesAuthChallengeRequest(device: device, nonce: UUID().uuidString)
        let data = try await transport.post(
            path: HermesAPIPaths.authChallenge,
            body: JSONEncoder().encode(request),
            bearerToken: nil
        )
        return try JSONDecoder().decode(HermesAuthChallengeResponse.self, from: data)
    }

    private static func normalizeApprovalDecision(_ raw: String) -> String {
        switch raw.lowercased() {
        case "reject": return "deny"
        default: return raw.lowercased()
        }
    }

    private func decodeCollection<T: Decodable>(_ type: T.Type, from data: Data) throws -> [T] {
        if let items = try? JSONDecoder().decode([T].self, from: data) {
            return items
        }
        if let wrapper = try? JSONDecoder().decode(HermesCollectionWrapper<T>.self, from: data) {
            return wrapper.items
        }
        if let dataWrapper = try? JSONDecoder().decode(HermesCollectionDataWrapper<T>.self, from: data) {
            return dataWrapper.data
        }
        if let resultsWrapper = try? JSONDecoder().decode(HermesCollectionResultsWrapper<T>.self, from: data) {
            return resultsWrapper.results
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
            HermesConversationEvent(eventId: "demo-event-1", author: "Hermes", body: "Demo realtime stream active.", timestamp: "now"),
            HermesConversationEvent(eventId: "demo-event-2", author: "You", body: "Review the latest approvals.", timestamp: "just now"),
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
        let handle = HermesRealtimeStreamHandle()
        handle.start { _ in
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
        }
        return handle
    }
}

private struct HermesCollectionWrapper<T: Decodable>: Decodable {
    let items: [T]
}

private struct HermesCollectionDataWrapper<T: Decodable>: Decodable {
    let data: [T]
}

private struct HermesCollectionResultsWrapper<T: Decodable>: Decodable {
    let results: [T]
}

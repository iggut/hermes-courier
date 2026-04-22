import Foundation

/// Canonical paths aligned with `shared/contract/hermes-courier-api.yaml`.
enum HermesAPIPaths {
    static let authChallenge = "/v1/auth/challenge"
    static let authResponse = "/v1/auth/response"
    static let dashboard = "/v1/dashboard"
    static let sessions = "/v1/sessions"
    static let approvals = "/v1/approvals"
    static let conversation = "/v1/conversation"
    /// Mobile uses a WebSocket at this path; the contract describes live events.
    static let eventsStream = "/v1/events"

    static func approvalDecision(approvalId: String) -> String {
        "/v1/approvals/\(approvalId)/decision"
    }

    static func sessionDetail(sessionId: String) -> String {
        "/v1/sessions/\(sessionId)"
    }

    static func sessionControlAction(sessionId: String) -> String {
        "/v1/sessions/\(sessionId)/actions"
    }

    static func sessionActionEndpoint(sessionId: String, action: String) -> String {
        "/v1/sessions/\(sessionId)/\(action)"
    }

    // Phase-1 WebUI-parity surfaces. Gateways that have not implemented these
    // yet return an `UnavailablePayload` (see shared/contract/README.md).
    static let skills = "/v1/skills"
    static let memory = "/v1/memory"
    static let cron = "/v1/cron"
    static let logs = "/v1/logs"

    static func skillDetail(skillId: String) -> String {
        "/v1/skills/\(skillId)"
    }

    static func memoryDetail(memoryId: String) -> String {
        "/v1/memory/\(memoryId)"
    }

    static func cronDetail(cronId: String) -> String {
        "/v1/cron/\(cronId)"
    }
}

struct HermesDeviceIdentity: Codable, Hashable {
    let deviceId: String
    let platform: String
    let appVersion: String
    let publicKeyFingerprint: String
}

struct HermesAuthChallengeRequest: Codable, Hashable {
    let device: HermesDeviceIdentity
    let nonce: String
}

struct HermesAuthChallengeResponse: Codable, Hashable {
    let challengeId: String
    let nonce: String
    let expiresAt: String
    let trustLevel: String
}

struct HermesAuthResponseRequest: Codable, Hashable {
    let challengeId: String
    let signedNonce: String
    let device: HermesDeviceIdentity
}

struct HermesAuthSession: Codable, Hashable {
    let sessionId: String
    let accessToken: String
    let refreshToken: String
    let expiresAt: String
    let gatewayUrl: String
    let mtlsRequired: Bool
    let scope: [String]
}

struct HermesDashboardSnapshot: Codable, Hashable {
    let activeSessionCount: Int
    let pendingApprovalCount: Int
    let lastSyncLabel: String
    let connectionState: String
}

struct HermesSessionSummary: Codable, Identifiable, Hashable {
    var id: String { sessionId }
    let sessionId: String
    let title: String
    let status: String
    let updatedAt: String
}

struct HermesApprovalSummary: Codable, Identifiable, Hashable {
    var id: String { approvalId }
    let approvalId: String
    let title: String
    let detail: String
    let requiresBiometrics: Bool
}

struct HermesConversationEvent: Codable, Identifiable, Hashable {
    var id: String { eventId }
    let eventId: String
    let author: String
    let body: String
    let timestamp: String
}

struct HermesApprovalDecisionBody: Codable, Hashable {
    let decision: String
    let reason: String?
}

struct HermesApprovalActionResult: Codable, Hashable {
    let approvalId: String
    let action: String
    let status: String
    let detail: String
    let updatedAt: String
}

struct HermesSessionControlActionResult: Codable, Hashable {
    let sessionId: String
    let action: String
    let status: String
    let detail: String
    let updatedAt: String
    let endpoint: String?
    let supported: Bool?

    init(
        sessionId: String,
        action: String,
        status: String,
        detail: String,
        updatedAt: String,
        endpoint: String? = nil,
        supported: Bool? = nil
    ) {
        self.sessionId = sessionId
        self.action = action
        self.status = status
        self.detail = detail
        self.updatedAt = updatedAt
        self.endpoint = endpoint
        self.supported = supported
    }
}

struct HermesRealtimeEnvelope: Codable, Hashable {
    let type: String
    let dashboard: HermesDashboardSnapshot?
    let sessions: [HermesSessionSummary]?
    let approvals: [HermesApprovalSummary]?
    let conversation: HermesConversationEvent?
    let approvalResult: HermesApprovalActionResult?
    let sessionControlResult: HermesSessionControlActionResult?
    let eventId: String?
    let eventTimestamp: String?

    enum CodingKeys: String, CodingKey {
        case type
        case kind
        case dashboard
        case sessions
        case approvals
        case conversation
        case event
        case approvalResult
        case approval_action
        case sessionControlResult
        case session_control_action
        case eventId
        case id
        case timestamp
    }

    init(
        type: String,
        dashboard: HermesDashboardSnapshot? = nil,
        sessions: [HermesSessionSummary]? = nil,
        approvals: [HermesApprovalSummary]? = nil,
        conversation: HermesConversationEvent? = nil,
        approvalResult: HermesApprovalActionResult? = nil,
        sessionControlResult: HermesSessionControlActionResult? = nil,
        eventId: String? = nil,
        eventTimestamp: String? = nil
    ) {
        self.type = type
        self.dashboard = dashboard
        self.sessions = sessions
        self.approvals = approvals
        self.conversation = conversation
        self.approvalResult = approvalResult
        self.sessionControlResult = sessionControlResult
        self.eventId = eventId
        self.eventTimestamp = eventTimestamp
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let kind = try c.decodeIfPresent(String.self, forKey: .kind)
        self.type = try c.decodeIfPresent(String.self, forKey: .type) ?? kind ?? "event"
        self.dashboard = try c.decodeIfPresent(HermesDashboardSnapshot.self, forKey: .dashboard)
        self.sessions = try c.decodeIfPresent([HermesSessionSummary].self, forKey: .sessions)
        self.approvals = try c.decodeIfPresent([HermesApprovalSummary].self, forKey: .approvals)
        let conv = try c.decodeIfPresent(HermesConversationEvent.self, forKey: .conversation)
        let evt = try c.decodeIfPresent(HermesConversationEvent.self, forKey: .event)
        self.conversation = conv ?? evt
        let ar = try c.decodeIfPresent(HermesApprovalActionResult.self, forKey: .approvalResult)
        let ar2 = try c.decodeIfPresent(HermesApprovalActionResult.self, forKey: .approval_action)
        self.approvalResult = ar ?? ar2
        let scr = try c.decodeIfPresent(HermesSessionControlActionResult.self, forKey: .sessionControlResult)
        let scr2 = try c.decodeIfPresent(HermesSessionControlActionResult.self, forKey: .session_control_action)
        self.sessionControlResult = scr ?? scr2
        self.eventId = try c.decodeIfPresent(String.self, forKey: .eventId) ?? c.decodeIfPresent(String.self, forKey: .id)
        self.eventTimestamp = try c.decodeIfPresent(String.self, forKey: .timestamp)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(type, forKey: .type)
        try c.encodeIfPresent(dashboard, forKey: .dashboard)
        try c.encodeIfPresent(sessions, forKey: .sessions)
        try c.encodeIfPresent(approvals, forKey: .approvals)
        try c.encodeIfPresent(conversation, forKey: .conversation)
        try c.encodeIfPresent(approvalResult, forKey: .approvalResult)
        try c.encodeIfPresent(sessionControlResult, forKey: .sessionControlResult)
        try c.encodeIfPresent(eventId, forKey: .eventId)
        try c.encodeIfPresent(eventTimestamp, forKey: .timestamp)
    }
}

struct HermesGatewaySettings: Hashable {
    var baseURL: String = "https://gateway.hermes.local"
    var certificatePath: String = ""
    var certificatePassword: String = ""
}

/// Wire decision values for approvals (parity with Android `normalizeApprovalDecisionWire` / queue migration).
public enum HermesApprovalWire {
    public static func normalizeDecision(_ raw: String) -> String {
        let lower = raw.lowercased()
        if lower == "reject" { return "deny" }
        return lower
    }

    public static func migrateQueuedAction(_ raw: String) -> String {
        raw.lowercased() == "reject" ? "deny" : raw
    }
}

/// UI labels: wire uses `deny` while surfaces say "Reject".
public enum HermesApprovalDisplay {
    private static func normalizedAction(_ action: String) -> String {
        action.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    /// Short verb for inline labels (buttons, queue rows, status snippets).
    public static func userFacingVerb(for action: String) -> String {
        switch normalizedAction(action) {
        case "deny", "reject":
            return "Reject"
        case "approve":
            return "Approve"
        default:
            return action.capitalized
        }
    }

    /// Navigation titles for the approval note sheet (matches Android dialog titles).
    public static func decisionSheetNavigationTitle(for wireAction: String) -> String {
        switch normalizedAction(wireAction) {
        case "approve":
            return "Approve approval"
        case "deny", "reject":
            return "Reject approval"
        default:
            return "\(userFacingVerb(for: wireAction)) approval"
        }
    }
}

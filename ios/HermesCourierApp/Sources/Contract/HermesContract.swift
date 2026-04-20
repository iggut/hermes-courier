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

struct HermesRealtimeEnvelope: Codable, Hashable {
    let type: String
    let dashboard: HermesDashboardSnapshot?
    let sessions: [HermesSessionSummary]?
    let approvals: [HermesApprovalSummary]?
    let conversation: HermesConversationEvent?
    let approvalResult: HermesApprovalActionResult?
}

struct HermesGatewaySettings: Hashable {
    var baseURL: String = "https://gateway.hermes.local"
    var certificatePath: String = ""
    var certificatePassword: String = ""
}

/// UI labels: wire uses `deny` while surfaces say "Reject".
enum HermesApprovalDisplay {
    private static func normalizedAction(_ action: String) -> String {
        action.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    /// Short verb for inline labels (buttons, queue rows, status snippets).
    static func userFacingVerb(for action: String) -> String {
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
    static func decisionSheetNavigationTitle(for wireAction: String) -> String {
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

import Foundation

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

struct HermesApprovalActionRequest: Codable, Hashable {
    let approvalId: String
    let action: String
    let note: String?
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

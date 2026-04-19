
import Foundation

struct HermesDeviceIdentity: Hashable {
    let deviceId: String
    let platform: String
    let appVersion: String
    let publicKeyFingerprint: String
}

struct HermesAuthChallengeRequest: Hashable {
    let device: HermesDeviceIdentity
    let nonce: String
}

struct HermesAuthChallengeResponse: Hashable {
    let challengeId: String
    let nonce: String
    let expiresAt: String
    let trustLevel: String
}

struct HermesAuthSession: Hashable {
    let sessionId: String
    let accessToken: String
    let refreshToken: String
    let expiresAt: String
    let gatewayUrl: String
    let mtlsRequired: Bool
    let scope: [String]
}

struct HermesDashboardSnapshot: Hashable {
    let activeSessionCount: Int
    let pendingApprovalCount: Int
    let lastSyncLabel: String
    let connectionState: String
}

struct HermesSessionSummary: Identifiable, Hashable {
    let id = UUID()
    let sessionId: String
    let title: String
    let status: String
    let updatedAt: String
}

struct HermesApprovalSummary: Identifiable, Hashable {
    let id = UUID()
    let approvalId: String
    let title: String
    let detail: String
    let requiresBiometrics: Bool
}

struct HermesConversationEvent: Identifiable, Hashable {
    let id = UUID()
    let eventId: String
    let author: String
    let body: String
    let timestamp: String
}

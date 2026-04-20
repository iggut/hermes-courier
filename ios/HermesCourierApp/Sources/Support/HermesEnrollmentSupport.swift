import Foundation

struct HermesEnrollmentPayload: Codable, Hashable {
    let gatewayUrl: String
    let deviceId: String
    let publicKeyFingerprint: String
    let appVersion: String
    let issuedAt: String
}

struct HermesQueuedApprovalAction: Codable, Hashable {
    let approvalId: String
    let action: String
    let note: String?
    let createdAt: TimeInterval
}

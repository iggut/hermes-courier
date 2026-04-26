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

enum HermesEnrollmentSupport {
    static func extractPayloadFromURI(_ uri: String) -> HermesEnrollmentPayload? {
        guard let url = URL(string: uri),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.scheme == "hermes",
              components.host == "enroll",
              let queryItems = components.queryItems,
              let payloadItem = queryItems.first(where: { $0.name == "payload" }),
              let payloadBase64 = payloadItem.value,
              let payloadData = Data(base64Encoded: payloadBase64) else {
            return nil
        }
        return try? JSONDecoder().decode(HermesEnrollmentPayload.self, from: payloadData)
    }
}

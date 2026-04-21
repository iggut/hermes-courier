
import Foundation

#if canImport(UIKit)
import UIKit
#endif

protocol HermesAuthManaging {
    func bootstrapSession() async throws -> HermesAuthSession
}

final class HermesAuthManager: HermesAuthManaging {
    private let gatewayClient: HermesGatewayClientProtocol
    private let deviceProvider: HermesDeviceIdentity

    init(
        gatewayClient: HermesGatewayClientProtocol = HermesGatewayClient(),
        deviceProvider: HermesDeviceIdentity = HermesDeviceIdentity(
            deviceId: HermesAuthManager.makeDeviceID(),
            platform: "ios",
            appVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.1.0",
            publicKeyFingerprint: "pending-keychain-bootstrap"
        )
    ) {
        self.gatewayClient = gatewayClient
        self.deviceProvider = deviceProvider
    }

    func bootstrapSession() async throws -> HermesAuthSession {
        try await gatewayClient.bootstrap(device: deviceProvider)
    }

    static func makeDeviceID() -> String {
        #if canImport(UIKit)
        let vendor = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        return "ios-\(vendor)"
        #else
        return "ios-\(UUID().uuidString)"
        #endif
    }
}

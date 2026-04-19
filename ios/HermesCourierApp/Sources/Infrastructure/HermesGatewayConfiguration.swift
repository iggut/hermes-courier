import Foundation

struct HermesGatewayConfiguration: Hashable {
    let baseURL: URL
    let mtlsIdentityURL: URL?
    let mtlsIdentityPassword: String?

    static func load() -> HermesGatewayConfiguration {
        let defaults = UserDefaults.standard
        let baseURLString = defaults.string(forKey: "hermes.gateway.baseURL") ?? "https://gateway.hermes.local"
        let baseURL = URL(string: baseURLString) ?? URL(string: "https://gateway.hermes.local")!
        let identityPath = defaults.string(forKey: "hermes.gateway.identityPath")
        let password = HermesKeychainSettingsStore().loadCertificatePassword()
        return HermesGatewayConfiguration(
            baseURL: baseURL,
            mtlsIdentityURL: identityPath.map { URL(fileURLWithPath: $0) },
            mtlsIdentityPassword: password
        )
    }

    static func save(_ settings: HermesGatewaySettings) {
        let defaults = UserDefaults.standard
        defaults.set(settings.baseURL, forKey: "hermes.gateway.baseURL")
        defaults.set(settings.certificatePath, forKey: "hermes.gateway.identityPath")

        let keychain = HermesKeychainSettingsStore()
        if settings.certificatePassword.isEmpty {
            keychain.clearCertificatePassword()
        } else {
            try? keychain.saveCertificatePassword(settings.certificatePassword)
        }
    }

    static func importCertificate(from sourceURL: URL) throws -> URL {
        let fileManager = FileManager.default
        let directory = try fileManager.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("HermesCourier", isDirectory: true)
            .appendingPathComponent("Certificates", isDirectory: true)
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = directory.appendingPathComponent("gateway-mtls.p12")
        if fileManager.fileExists(atPath: destination.path) {
            try fileManager.removeItem(at: destination)
        }
        try fileManager.copyItem(at: sourceURL, to: destination)
        return destination
    }
}

import Foundation

struct HermesGatewayConfiguration: Hashable {
    let baseURL: URL
    let mtlsIdentityURL: URL?
    let mtlsIdentityPassword: String?

    static func load() -> HermesGatewayConfiguration {
        let keychain = HermesKeychainSettingsStore()
        let defaults = UserDefaults.standard

        // Migration from UserDefaults to Keychain
        if let legacyBaseURL = defaults.string(forKey: "hermes.gateway.baseURL") {
            do {
                try keychain.saveBaseURL(legacyBaseURL)
                defaults.removeObject(forKey: "hermes.gateway.baseURL")
            } catch {
                // Keep in UserDefaults if Keychain save fails
            }
        }
        if let legacyIdentityPath = defaults.string(forKey: "hermes.gateway.identityPath") {
            do {
                try keychain.saveIdentityPath(legacyIdentityPath)
                defaults.removeObject(forKey: "hermes.gateway.identityPath")
            } catch {
                // Keep in UserDefaults if Keychain save fails
            }
        }

        let baseURLString = keychain.loadBaseURL() ?? "https://gateway.hermes.local"
        let baseURL = URL(string: baseURLString) ?? URL(string: "https://gateway.hermes.local")!
        let identityPath = keychain.loadIdentityPath()
        let password = keychain.loadCertificatePassword()

        return HermesGatewayConfiguration(
            baseURL: baseURL,
            mtlsIdentityURL: identityPath.map { URL(fileURLWithPath: $0) },
            mtlsIdentityPassword: password
        )
    }

    static func save(_ settings: HermesGatewaySettings) {
        let keychain = HermesKeychainSettingsStore()
        try? keychain.saveBaseURL(settings.baseURL)
        try? keychain.saveIdentityPath(settings.certificatePath)

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

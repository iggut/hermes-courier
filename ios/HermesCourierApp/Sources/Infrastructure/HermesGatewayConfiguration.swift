
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
        let password = defaults.string(forKey: "hermes.gateway.identityPassword")
        return HermesGatewayConfiguration(
            baseURL: baseURL,
            mtlsIdentityURL: identityPath.map { URL(fileURLWithPath: $0) },
            mtlsIdentityPassword: password
        )
    }
}


import Foundation

struct HermesClientIdentity {
    let identity: Any
    let certificates: [Any]?
}

protocol HermesClientIdentityProviding {
    func loadIdentity() throws -> HermesClientIdentity?
}

#if canImport(Security)
import Security

final class HermesPKCS12IdentityProvider: HermesClientIdentityProviding {
    private let configuration: HermesGatewayConfiguration

    init(configuration: HermesGatewayConfiguration) {
        self.configuration = configuration
    }

    func loadIdentity() throws -> HermesClientIdentity? {
        guard let url = configuration.mtlsIdentityURL else { return nil }
        let data = try Data(contentsOf: url)
        let options = [kSecImportExportPassphrase as String: configuration.mtlsIdentityPassword ?? ""]
        var rawItems: CFArray?
        let status = SecPKCS12Import(data as CFData, options as CFDictionary, &rawItems)
        guard status == errSecSuccess,
              let items = rawItems as? [[String: Any]],
              let first = items.first,
              let identityValue = first[kSecImportItemIdentity as String] else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        let identity = identityValue as! SecIdentity
        let certificates = first[kSecImportItemCertChain as String] as? [SecCertificate]
        return HermesClientIdentity(identity: identity, certificates: certificates)
    }
}
#else
final class HermesPKCS12IdentityProvider: HermesClientIdentityProviding {
    init(configuration: HermesGatewayConfiguration) {}
    func loadIdentity() throws -> HermesClientIdentity? { nil }
}
#endif

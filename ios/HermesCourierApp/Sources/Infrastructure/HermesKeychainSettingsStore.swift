import Foundation
import Security

final class HermesKeychainSettingsStore {
    private let service = "com.hermescourier.gateway-settings"
    private let passwordAccount = "certificatePassword"
    private let baseURLAccount = "baseURL"
    private let identityPathAccount = "identityPath"

    func saveCertificatePassword(_ password: String) throws {
        try save(password, forAccount: passwordAccount)
    }

    func loadCertificatePassword() -> String? {
        load(forAccount: passwordAccount)
    }

    func clearCertificatePassword() {
        delete(forAccount: passwordAccount)
    }

    func saveBaseURL(_ url: String) throws {
        try save(url, forAccount: baseURLAccount)
    }

    func loadBaseURL() -> String? {
        load(forAccount: baseURLAccount)
    }

    func clearBaseURL() {
        delete(forAccount: baseURLAccount)
    }

    func saveIdentityPath(_ path: String) throws {
        try save(path, forAccount: identityPathAccount)
    }

    func loadIdentityPath() -> String? {
        load(forAccount: identityPathAccount)
    }

    func clearIdentityPath() {
        delete(forAccount: identityPathAccount)
    }

    private func save(_ value: String, forAccount account: String) throws {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        let attributes: [String: Any] = query.merging([
            kSecValueData as String: data,
        ], uniquingKeysWith: { $1 })
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Unable to save \(account) to Keychain"])
        }
    }

    private func load(forAccount account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func delete(forAccount account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

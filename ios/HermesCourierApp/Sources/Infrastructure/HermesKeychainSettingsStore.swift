import Foundation
import Security

final class HermesKeychainSettingsStore {
    private let service = "com.hermescourier.gateway-settings"
    private let passwordAccount = "certificatePassword"

    func saveCertificatePassword(_ password: String) throws {
        let data = Data(password.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: passwordAccount,
        ]
        SecItemDelete(query as CFDictionary)
        let attributes: [String: Any] = query.merging([
            kSecValueData as String: data,
        ], uniquingKeysWith: { $1 })
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Unable to save certificate password to Keychain"])
        }
    }

    func loadCertificatePassword() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: passwordAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func clearCertificatePassword() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: passwordAccount,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

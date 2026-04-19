
import Foundation

protocol HermesChallengeSigning {
    func publicKeyFingerprint() throws -> String
    func sign(nonce: String, device: HermesDeviceIdentity) throws -> String
}

#if canImport(Security) && canImport(CryptoKit)
import Security
import CryptoKit

final class HermesKeychainChallengeSigner: HermesChallengeSigning {
    private let service = "com.hermescourier.signing"
    private let account = "device-key"

    func publicKeyFingerprint() throws -> String {
        let key = try loadOrCreateKey()
        return SHA256.hash(data: key.publicKey.rawRepresentation).map { String(format: "%02x", $0) }.joined()
    }

    func sign(nonce: String, device: HermesDeviceIdentity) throws -> String {
        let key = try loadOrCreateKey()
        let payload = buildPayload(nonce: nonce, device: device)
        let signature = try key.signature(for: Data(payload.utf8))
        return Data(signature.derRepresentation).base64EncodedString()
    }

    private func buildPayload(nonce: String, device: HermesDeviceIdentity) -> String {
        [nonce, device.deviceId, device.platform, device.appVersion].joined(separator: "|")
    }

    private func loadOrCreateKey() throws -> P256.Signing.PrivateKey {
        if let data = try loadKeyData() {
            return try P256.Signing.PrivateKey(rawRepresentation: data)
        }
        let key = P256.Signing.PrivateKey()
        try saveKeyData(key.rawRepresentation)
        return key
    }

    private func saveKeyData(_ data: Data) throws {
        let query = baseQuery().merging([kSecValueData as String: data], uniquingKeysWith: { $1 })
        let status = SecItemAdd(query as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let updateStatus = SecItemUpdate(baseQuery() as CFDictionary, [kSecValueData as String: data] as CFDictionary)
            guard updateStatus == errSecSuccess else {
                throw NSError(domain: NSOSStatusErrorDomain, code: Int(updateStatus))
            }
        } else if status != errSecSuccess {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
    }

    private func loadKeyData() throws -> Data? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status != errSecItemNotFound else { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        return data
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
#else
final class HermesKeychainChallengeSigner: HermesChallengeSigning {
    func publicKeyFingerprint() throws -> String { "demo-fingerprint" }
    func sign(nonce: String, device: HermesDeviceIdentity) throws -> String {
        "demo-signature-\(nonce)-\(device.deviceId)"
    }
}
#endif

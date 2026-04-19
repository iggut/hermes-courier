
import Foundation

protocol HermesTokenStoring {
    func save(_ session: HermesAuthSession) throws
    func load() throws -> HermesAuthSession?
    func clear() throws
}

#if canImport(Security)
import Security

final class HermesKeychainTokenStore: HermesTokenStoring {
    private let service = "com.hermescourier.tokens"
    private let account = "current-session"

    func save(_ session: HermesAuthSession) throws {
        let data = try JSONEncoder().encode(session)
        try upsert(data: data)
    }

    func load() throws -> HermesAuthSession? {
        var query: [String: Any] = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status != errSecItemNotFound else { return nil }
        guard status == errSecSuccess,
              let data = result as? Data else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        return try JSONDecoder().decode(HermesAuthSession.self, from: data)
    }

    func clear() throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
    }

    private func upsert(data: Data) throws {
        var query = baseQuery()
        let attributes: [String: Any] = [kSecValueData as String: data]
        let status = SecItemAdd(query.merging(attributes, uniquingKeysWith: { $1 }) as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
            guard updateStatus == errSecSuccess else {
                throw NSError(domain: NSOSStatusErrorDomain, code: Int(updateStatus))
            }
        } else if status != errSecSuccess {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
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
final class HermesKeychainTokenStore: HermesTokenStoring {
    private var session: HermesAuthSession?
    func save(_ session: HermesAuthSession) throws { self.session = session }
    func load() throws -> HermesAuthSession? { session }
    func clear() throws { session = nil }
}
#endif

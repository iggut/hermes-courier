import Foundation
import Security

protocol HermesHTTPTransport {
    func get(path: String, bearerToken: String?) async throws -> Data
    func post(path: String, body: Data, bearerToken: String?) async throws -> Data
    func webSocketTask(path: String, bearerToken: String?) -> URLSessionWebSocketTask
}

final class HermesURLSessionTransport: NSObject, HermesHTTPTransport, URLSessionDelegate {
    private let configuration: HermesGatewayConfiguration
    private lazy var session: URLSession = {
        let session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
        return session
    }()

    init(configuration: HermesGatewayConfiguration) {
        self.configuration = configuration
        super.init()
    }

    func get(path: String, bearerToken: String?) async throws -> Data {
        try await perform(path: path, method: "GET", body: nil, bearerToken: bearerToken)
    }

    func post(path: String, body: Data, bearerToken: String?) async throws -> Data {
        try await perform(path: path, method: "POST", body: body, bearerToken: bearerToken)
    }

    func webSocketTask(path: String, bearerToken: String?) -> URLSessionWebSocketTask {
        let request = makeRequest(path: path, method: "GET", body: nil, bearerToken: bearerToken)
        return session.webSocketTask(with: request)
    }

    private func perform(path: String, method: String, body: Data?, bearerToken: String?) async throws -> Data {
        let request = makeRequest(path: path, method: method, body: body, bearerToken: bearerToken)
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let bodyString = String(data: data, encoding: .utf8) ?? ""
            throw NSError(domain: "HermesURLSessionTransport", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: "HTTP \(method) \(path) failed with \(httpResponse.statusCode): \(bodyString)"])
        }
        return data
    }

    private func makeRequest(path: String, method: String, body: Data?, bearerToken: String?) -> URLRequest {
        let url = configuration.baseURL.appendingPathComponent(path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let bearerToken {
            request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodClientCertificate else {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        do {
            let identity = try loadIdentity()
            if let secIdentity = identity.identity as? SecIdentity {
                let credential = URLCredential(identity: secIdentity, certificates: identity.certificates, persistence: .forSession)
                completionHandler(.useCredential, credential)
            } else {
                completionHandler(.performDefaultHandling, nil)
            }
        } catch {
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }

    private func loadIdentity() throws -> SecPKCS12Identity {
        guard let url = configuration.mtlsIdentityURL else {
            throw NSError(domain: "HermesURLSessionTransport", code: 1, userInfo: [NSLocalizedDescriptionKey: "Missing mTLS identity bundle"])
        }
        guard let data = try? Data(contentsOf: url) else {
            throw NSError(domain: "HermesURLSessionTransport", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unable to read identity bundle"])
        }
        let options: NSDictionary = [kSecImportExportPassphrase as String: configuration.mtlsIdentityPassword ?? ""]
        var items: CFArray?
        let status = SecPKCS12Import(data as CFData, options, &items)
        guard status == errSecSuccess,
              let array = items as? [[String: Any]],
              let first = array.first,
              let identity = first[kSecImportItemIdentity as String] as? SecIdentity else {
            throw NSError(domain: "HermesURLSessionTransport", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Unable to import mTLS identity"])
        }
        let certificates = (first[kSecImportItemCertChain as String] as? [SecCertificate]) ?? []
        return SecPKCS12Identity(identity: identity, certificates: certificates)
    }
}

private struct SecPKCS12Identity {
    let identity: SecIdentity
    let certificates: [SecCertificate]
}


import Foundation

#if canImport(Security)
import Security
#endif

protocol HermesHTTPTransport {
    func get(path: String, bearerToken: String?) async throws -> Data
    func post(path: String, body: Data, bearerToken: String?) async throws -> Data
}

final class HermesURLSessionTransport: NSObject, HermesHTTPTransport, URLSessionDelegate {
    private let configuration: HermesGatewayConfiguration
    private let identityProvider: HermesClientIdentityProviding
    private var session: URLSession!

    init(configuration: HermesGatewayConfiguration) {
        self.configuration = configuration
        self.identityProvider = HermesPKCS12IdentityProvider(configuration: configuration)
        super.init()
        let sessionConfiguration = URLSessionConfiguration.ephemeral
        sessionConfiguration.waitsForConnectivity = true
        sessionConfiguration.timeoutIntervalForRequest = 30
        sessionConfiguration.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: sessionConfiguration, delegate: self, delegateQueue: nil)
    }

    func get(path: String, bearerToken: String?) async throws -> Data {
        try await perform(path: path, method: "GET", body: nil, bearerToken: bearerToken)
    }

    func post(path: String, body: Data, bearerToken: String?) async throws -> Data {
        try await perform(path: path, method: "POST", body: body, bearerToken: bearerToken)
    }

    private func perform(path: String, method: String, body: Data?, bearerToken: String?) async throws -> Data {
        let url = configuration.baseURL.appendingPathComponent(path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let bearerToken {
            request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = body

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            let payload = String(data: data, encoding: .utf8) ?? ""
            throw NSError(domain: "HermesURLSessionTransport", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: payload])
        }
        return data
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        #if canImport(Security)
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodClientCertificate,
           let identity = try? identityProvider.loadIdentity() {
            if let secIdentity = identity.identity as? SecIdentity {
                let credential = URLCredential(identity: secIdentity, certificates: identity.certificates, persistence: .forSession)
                completionHandler(.useCredential, credential)
                return
            }
        }
        #endif
        completionHandler(.performDefaultHandling, nil)
    }
}
